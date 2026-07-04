import asyncio
import json
import logging
import os
import shutil
import time
import zipfile
from datetime import datetime, timezone
from pathlib import Path

from .models import WorldConfig

logger = logging.getLogger("grandterrain.generator")

DATA_DIR = Path(os.getenv("DATA_DIR", "/app/data"))
JOBS_DIR = DATA_DIR / "jobs"
WORLDS_DIR = DATA_DIR / "worlds"
MOD_JAR = Path(os.getenv("MOD_JAR_PATH", "/app/data/grandterrain.jar"))
FABRIC_API_JAR = Path(os.getenv("FABRIC_API_JAR_PATH", "/app/data/fabric-api.jar"))
SERVER_JAR = Path(os.getenv("SERVER_JAR_PATH", "/app/data/fabric-server-launch.jar"))

STARTUP_TIMEOUT = int(os.getenv("SERVER_STARTUP_TIMEOUT", "180"))
GENERATION_SETTLE = int(os.getenv("GENERATION_SETTLE_TIME", "30"))
RETENTION_HOURS = int(os.getenv("JOB_RETENTION_HOURS", "24"))

# In-flight server processes, tracked so app shutdown can kill them. A cancelled
# background task raises CancelledError (a BaseException), which `except Exception`
# handlers miss — without this, shutdown mid-generation orphans a 2G JVM.
live_procs: set = set()

SERVER_PROPERTIES = """\
server-port=0
online-mode=false
enable-command-block=false
spawn-protection=0
max-tick-time=-1
level-name=world
level-type=grandterrain:grandterrain
motd=GrandTerrain World Generator
max-players=0
"""


def ensure_dirs() -> None:
    JOBS_DIR.mkdir(parents=True, exist_ok=True)
    WORLDS_DIR.mkdir(parents=True, exist_ok=True)


def reconcile_stale_jobs() -> None:
    """Mark jobs stuck in pending/generating as failed.

    Runs at startup, before any new job can begin: after a restart no task can
    own these jobs, so without this a client would poll them forever.
    """
    if not JOBS_DIR.exists():
        return
    for entry in JOBS_DIR.iterdir():
        meta_path = entry / "meta.json"
        if not meta_path.exists():
            continue
        try:
            meta = json.loads(meta_path.read_text())
        except ValueError:
            continue
        if meta.get("status") in ("pending", "generating"):
            meta["status"] = "failed"
            meta_path.write_text(json.dumps(meta, indent=2))
            logger.warning("Marked stale job %s as failed (worker restarted)", entry.name)
        # A generation killed mid-flight can leave its server instance behind.
        shutil.rmtree(entry / "server", ignore_errors=True)


def sweep_old_jobs() -> None:
    """Delete job dirs and world zips older than JOB_RETENTION_HOURS.

    Without retention every job leaves a meta/log dir and a multi-hundred-MB
    zip forever, and a full disk takes the whole service down.
    """
    cutoff = time.time() - RETENTION_HOURS * 3600
    for base in (JOBS_DIR, WORLDS_DIR):
        if not base.exists():
            continue
        for entry in base.iterdir():
            try:
                if entry.stat().st_mtime < cutoff:
                    if entry.is_dir():
                        shutil.rmtree(entry, ignore_errors=True)
                    else:
                        entry.unlink(missing_ok=True)
            except OSError:
                pass


def write_config_properties(job_id: str, config: WorldConfig) -> Path:
    job_dir = JOBS_DIR / job_id
    job_dir.mkdir(exist_ok=True)
    props_path = job_dir / "grandterrain.properties"
    props_path.write_text(config.to_properties())
    return props_path


def _update_meta(job_id: str, updates: dict) -> None:
    meta_path = JOBS_DIR / job_id / "meta.json"
    if meta_path.exists():
        meta = json.loads(meta_path.read_text())
    else:
        meta = {}
    meta.update(updates)
    meta_path.write_text(json.dumps(meta, indent=2))


def _setup_server_instance(job_id: str, config: WorldConfig) -> Path:
    instance_dir = JOBS_DIR / job_id / "server"
    if instance_dir.exists():
        shutil.rmtree(instance_dir)
    instance_dir.mkdir(parents=True)

    mods_dir = instance_dir / "mods"
    mods_dir.mkdir()
    shutil.copy(MOD_JAR, mods_dir / MOD_JAR.name)
    if FABRIC_API_JAR.exists():
        shutil.copy(FABRIC_API_JAR, mods_dir / FABRIC_API_JAR.name)

    config_dir = instance_dir / "config"
    config_dir.mkdir()
    (config_dir / "grandterrain.properties").write_text(config.to_properties())

    (instance_dir / "server.properties").write_text(SERVER_PROPERTIES)
    (instance_dir / "eula.txt").write_text("eula=true\n")

    if SERVER_JAR.exists():
        shutil.copy(SERVER_JAR, instance_dir / "fabric-server-launch.jar")

    return instance_dir


def _package_world(job_id: str, instance_dir: Path) -> Path:
    world_dir = instance_dir / "world"
    zip_path = WORLDS_DIR / f"{job_id}.zip"

    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        if world_dir.exists():
            for file_path in world_dir.rglob("*"):
                if file_path.is_file():
                    arcname = f"world/{file_path.relative_to(world_dir)}"
                    zf.write(file_path, arcname)
        else:
            zf.writestr("world/README.txt", "World generation requires a Fabric server.\n")

    return zip_path


async def generate_world(job_id: str, config: WorldConfig) -> Path | None:
    ensure_dirs()
    _update_meta(job_id, {
        "status": "generating",
        "started_at": datetime.now(timezone.utc).isoformat(),
    })

    instance_dir = _setup_server_instance(job_id, config)
    server_jar = instance_dir / "fabric-server-launch.jar"
    success = False

    if server_jar.exists():
        logger.info("Starting Fabric server for job %s", job_id)
        proc = await asyncio.create_subprocess_exec(
            "java", "-Xmx2G", "-jar", "fabric-server-launch.jar", "--nogui",
            cwd=str(instance_dir),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
            stdin=asyncio.subprocess.PIPE,
        )
        live_procs.add(proc)

        log_path = JOBS_DIR / job_id / "server.log"
        server_ready = False

        try:
            with open(log_path, "w") as log_file:
                async def read_output():
                    nonlocal server_ready
                    while True:
                        line = await proc.stdout.readline()
                        if not line:
                            break
                        decoded = line.decode("utf-8", errors="replace").rstrip()
                        log_file.write(decoded + "\n")
                        log_file.flush()
                        logger.debug("[mc] %s", decoded)
                        if "Done (" in decoded and ")!" in decoded:
                            server_ready = True
                            return

                # Total wall-clock budget for startup. Wrapping each readline
                # instead would reset the timer on every log line, so a
                # chatty-but-stuck server could hold the job slot forever.
                await asyncio.wait_for(read_output(), timeout=STARTUP_TIMEOUT)

                if server_ready:
                    success = True

                    async def drain_rest():
                        # Keep consuming stdout after "Done": a chatty server
                        # can fill the 64K pipe buffer during the settle/stop
                        # window and block right when we send "stop".
                        while True:
                            rest = await proc.stdout.readline()
                            if not rest:
                                return
                            log_file.write(rest.decode("utf-8", errors="replace"))

                    drain = asyncio.create_task(drain_rest())

                    logger.info("Server ready for job %s, letting chunks settle", job_id)
                    await asyncio.sleep(GENERATION_SETTLE)

                    logger.info("Stopping server for job %s", job_id)
                    proc.stdin.write(b"stop\n")
                    await proc.stdin.drain()

                    try:
                        await asyncio.wait_for(proc.wait(), timeout=60)
                    except asyncio.TimeoutError:
                        logger.warning("Server didn't stop cleanly, terminating")
                        proc.terminate()
                        await proc.wait()

                    try:
                        await asyncio.wait_for(drain, timeout=10)
                    except asyncio.TimeoutError:
                        drain.cancel()
                else:
                    logger.error("Server never became ready for job %s", job_id)
                    proc.terminate()
                    await proc.wait()

        except asyncio.TimeoutError:
            logger.error("Server startup timed out for job %s", job_id)
        except Exception:
            logger.exception("Error during world generation for job %s", job_id)
        finally:
            # Runs even on CancelledError (app shutdown), which the handlers
            # above miss — never leave the JVM running.
            if proc.returncode is None:
                proc.kill()
                try:
                    await asyncio.wait_for(proc.wait(), timeout=10)
                except (asyncio.TimeoutError, asyncio.CancelledError):
                    pass
            live_procs.discard(proc)
    else:
        logger.warning(
            "No server jar found at %s — cannot generate for job %s",
            server_jar, job_id,
        )

    if not success:
        shutil.rmtree(instance_dir, ignore_errors=True)
        _update_meta(job_id, {
            "status": "failed",
            "failed_at": datetime.now(timezone.utc).isoformat(),
        })
        logger.error("Generation failed for job %s", job_id)
        return None

    try:
        zip_path = _package_world(job_id, instance_dir)
    except Exception:
        # Without this a zip failure (e.g. disk full) strands the job in
        # "generating" until the next restart's reconcile pass.
        logger.exception("Failed to package world for job %s", job_id)
        shutil.rmtree(instance_dir, ignore_errors=True)
        _update_meta(job_id, {
            "status": "failed",
            "failed_at": datetime.now(timezone.utc).isoformat(),
        })
        return None

    shutil.rmtree(instance_dir, ignore_errors=True)

    _update_meta(job_id, {
        "status": "ready",
        "completed_at": datetime.now(timezone.utc).isoformat(),
        "world_zip": str(zip_path),
    })

    logger.info("Generation complete for job %s — zip at %s", job_id, zip_path)
    return zip_path
