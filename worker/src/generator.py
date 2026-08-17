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
MOD_JAR = Path(os.getenv("MOD_JAR_PATH", str(DATA_DIR / "grandterrain.jar")))
FABRIC_API_JAR = Path(os.getenv("FABRIC_API_JAR_PATH", str(DATA_DIR / "fabric-api.jar")))
SERVER_JAR = Path(os.getenv("SERVER_JAR_PATH", str(DATA_DIR / "fabric-server-launch.jar")))

STARTUP_TIMEOUT = int(os.getenv("SERVER_STARTUP_TIMEOUT", "180"))
GENERATION_SETTLE = int(os.getenv("GENERATION_SETTLE_TIME", "30"))
RETENTION_HOURS = int(os.getenv("JOB_RETENTION_HOURS", "24"))

# Pre-generation via the Chunky mod, driven over the server console.
# Radius is in blocks from spawn (2000 -> a 4000x4000 area); 0 disables.
CHUNKY_JAR = Path(os.getenv("CHUNKY_JAR_PATH", str(DATA_DIR / "chunky.jar")))
PREGEN_RADIUS = int(os.getenv("PREGEN_RADIUS", "2000"))
PREGEN_TIMEOUT = int(os.getenv("PREGEN_TIMEOUT", "21600"))

# Max heap for the generation server. 1024-tall chunks hold ~2.7x the block
# data of vanilla, and Chunky keeps many of them in flight: a 9.4k-chunk
# pre-gen reproducibly OOM'd at 2G during chunk save (PalettedContainer.pack),
# which killed the run at 99% and truncated the region files. 6G is sized for
# the default 2000-block radius; lower it only for small radii.
SERVER_MEMORY = os.getenv("SERVER_MEMORY", "6G")

# In-flight server processes, tracked so app shutdown can kill them. A cancelled
# background task raises CancelledError (a BaseException), which `except Exception`
# handlers miss — without this, shutdown mid-generation orphans the JVM.
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
            meta["error"] = "Worker restarted during generation"
            write_meta_atomic(meta_path, meta)
            logger.warning("Marked stale job %s as failed (worker restarted)", entry.name)
        # A generation killed mid-flight can leave its server instance behind.
        shutil.rmtree(entry / "server", ignore_errors=True)


def _read_meta(job_id: str) -> dict | None:
    """Return a job's meta dict, or None if it is missing/unreadable."""
    try:
        return json.loads((JOBS_DIR / job_id / "meta.json").read_text())
    except (FileNotFoundError, ValueError, OSError):
        return None


def _is_expired(entry: Path, meta: dict | None, cutoff: float) -> bool:
    """Age comes from meta created_at (the job's real age); st_mtime is only
    a fallback for unreadable meta, since a status write during generation
    would otherwise make a long job look brand new."""
    if meta and meta.get("created_at"):
        try:
            created = datetime.fromisoformat(meta["created_at"])
            if created.tzinfo is None:
                created = created.replace(tzinfo=timezone.utc)
            return created.timestamp() < cutoff
        except (ValueError, TypeError):
            pass
    return entry.stat().st_mtime < cutoff


def sweep_old_jobs() -> None:
    """Delete job dirs and world zips older than JOB_RETENTION_HOURS.

    Without retention every job leaves a meta/log dir and a multi-hundred-MB
    zip forever, and a full disk takes the whole service down. Jobs that are
    still pending/generating are never swept, however old they look.
    """
    cutoff = time.time() - RETENTION_HOURS * 3600
    # Zips first: their status/age comes from the job's meta, which the
    # JOBS_DIR pass deletes.
    for base in (WORLDS_DIR, JOBS_DIR):
        if not base.exists():
            continue
        for entry in base.iterdir():
            try:
                job_id = entry.name.split(".", 1)[0]
                meta = _read_meta(job_id)
                if meta and meta.get("status") in ("pending", "generating"):
                    continue
                if _is_expired(entry, meta, cutoff):
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


def write_meta_atomic(meta_path: Path, meta: dict) -> None:
    """Write meta.json via a temp file + rename so readers never see a partial file."""
    tmp = meta_path.with_name(meta_path.name + ".tmp")
    tmp.write_text(json.dumps(meta, indent=2))
    os.replace(tmp, meta_path)


def _update_meta(job_id: str, updates: dict) -> None:
    meta_path = JOBS_DIR / job_id / "meta.json"
    try:
        if meta_path.exists():
            meta = json.loads(meta_path.read_text())
        else:
            meta = {}
        meta.update(updates)
        write_meta_atomic(meta_path, meta)
    except (FileNotFoundError, ValueError, OSError):
        # The job dir may have been swept out from under us; never let this
        # escape into the background task and take the job's cleanup with it.
        logger.warning("Could not update meta for job %s", job_id, exc_info=True)


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
    if CHUNKY_JAR.exists() and PREGEN_RADIUS > 0:
        shutil.copy(CHUNKY_JAR, mods_dir / CHUNKY_JAR.name)

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
    # Write to a .part and rename at the end: the download endpoint checks
    # `zip_path.exists()`, so it must never see a half-written archive.
    part_path = WORLDS_DIR / f"{job_id}.zip.part"

    try:
        # ZIP_STORED: region files are already zlib-compressed, so deflating
        # them again costs 5-10x the CPU for a few percent of size.
        with zipfile.ZipFile(part_path, "w", zipfile.ZIP_STORED) as zf:
            if world_dir.exists():
                for file_path in world_dir.rglob("*"):
                    if file_path.is_file():
                        arcname = f"world/{file_path.relative_to(world_dir)}"
                        zf.write(file_path, arcname)
            else:
                zf.writestr("world/README.txt", "World generation requires a Fabric server.\n")
        os.replace(part_path, zip_path)
    except BaseException:
        part_path.unlink(missing_ok=True)
        raise

    return zip_path


async def _stop_proc(proc: asyncio.subprocess.Process) -> None:
    """Terminate a server process, escalating to kill; never waits forever.

    A JVM that ignores SIGTERM would otherwise hold the only job slot until
    the container restarts, 429ing every request in the meantime.
    """
    if proc.returncode is not None:
        return
    proc.terminate()
    try:
        await asyncio.wait_for(proc.wait(), timeout=30)
        return
    except asyncio.TimeoutError:
        logger.warning("Server ignored SIGTERM for 30s, killing")
    proc.kill()
    try:
        await asyncio.wait_for(proc.wait(), timeout=10)
    except asyncio.TimeoutError:
        logger.error("Server did not exit 10s after SIGKILL (pid %s)", proc.pid)


def _fail(job_id: str, error: str) -> None:
    _update_meta(job_id, {
        "status": "failed",
        "error": error,
        "failed_at": datetime.now(timezone.utc).isoformat(),
    })


async def generate_world(job_id: str, config: WorldConfig) -> Path | None:
    ensure_dirs()
    _update_meta(job_id, {
        "status": "generating",
        "started_at": datetime.now(timezone.utc).isoformat(),
    })

    loop = asyncio.get_running_loop()
    for name, jar in (("grandterrain.jar", MOD_JAR), ("fabric-server-launch.jar", SERVER_JAR)):
        if not jar.exists():
            # E.g. the mod jar never downloaded (bootstrap starts the API anyway).
            logger.error("Required jar missing for job %s: %s", job_id, jar)
            _fail(job_id, f"Required jar missing: {name}")
            return None
    try:
        # Jar copies are sync disk I/O; keep them off the event loop.
        instance_dir = await loop.run_in_executor(
            None, _setup_server_instance, job_id, config
        )
    except Exception:
        # Without this the job stays "generating" until the next restart.
        logger.exception("Failed to set up server instance for job %s", job_id)
        _fail(job_id, "Failed to set up server instance (see worker logs)")
        return None
    server_jar = instance_dir / "fabric-server-launch.jar"
    success = False
    error = "Generation failed (see worker logs)"

    if server_jar.exists():
        logger.info("Starting Fabric server for job %s", job_id)
        proc = await asyncio.create_subprocess_exec(
            "java", f"-Xmx{SERVER_MEMORY}", "-jar", "fabric-server-launch.jar", "--nogui",
            cwd=str(instance_dir),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.STDOUT,
            stdin=asyncio.subprocess.PIPE,
        )
        live_procs.add(proc)

        log_path = JOBS_DIR / job_id / "server.log"
        server_ready = False
        # An OOM during chunk save truncates region files, but the pre-gen
        # task has usually already reported "Task finished" — so the job would
        # otherwise be packaged and served as a silently corrupt world.
        fatal = False

        try:
            with open(log_path, "w") as log_file:
                def record(decoded: str) -> None:
                    nonlocal fatal
                    log_file.write(decoded + "\n")
                    log_file.flush()
                    if "OutOfMemoryError" in decoded:
                        fatal = True

                async def read_until(predicate):
                    """Consume stdout into the log until predicate matches.

                    Returns True on match, False on EOF (server exited).
                    """
                    while True:
                        line = await proc.stdout.readline()
                        if not line:
                            return False
                        decoded = line.decode("utf-8", errors="replace").rstrip()
                        record(decoded)
                        logger.debug("[mc] %s", decoded)
                        if predicate(decoded):
                            return True

                # Total wall-clock budget for startup. Wrapping each readline
                # instead would reset the timer on every log line, so a
                # chatty-but-stuck server could hold the job slot forever.
                server_ready = await asyncio.wait_for(
                    read_until(lambda l: "Done (" in l and ")!" in l),
                    timeout=STARTUP_TIMEOUT,
                )

                if server_ready:
                    success = True

                    chunky_loaded = (instance_dir / "mods" / CHUNKY_JAR.name).exists()
                    if chunky_loaded and PREGEN_RADIUS > 0:
                        logger.info(
                            "Pre-generating %d-block radius for job %s", PREGEN_RADIUS, job_id
                        )
                        proc.stdin.write(
                            f"chunky radius {PREGEN_RADIUS}\nchunky start\n".encode()
                        )
                        await proc.stdin.drain()
                        try:
                            finished = await asyncio.wait_for(
                                read_until(lambda l: "Task finished" in l),
                                timeout=PREGEN_TIMEOUT,
                            )
                            if not finished:
                                # EOF mid-pregen: the server crashed.
                                logger.error(
                                    "Server exited during pre-generation for job %s", job_id
                                )
                                success = False
                                error = "Server exited unexpectedly during pre-generation"
                        except asyncio.TimeoutError:
                            # Best-effort: the chunks generated so far are a
                            # valid (just smaller) pre-genned world. Cancel
                            # the task and flush regions before "stop" so the
                            # in-flight chunks land on disk.
                            logger.warning(
                                "Pre-generation timed out for job %s — packaging what completed",
                                job_id,
                            )
                            try:
                                proc.stdin.write(b"chunky cancel\nsave-all flush\n")
                                await proc.stdin.drain()
                            except (BrokenPipeError, ConnectionResetError):
                                logger.debug("Server pipe already closed for job %s", job_id)
                    async def drain_rest():
                        # Keep consuming stdout through the settle/stop window:
                        # a chatty server can fill the 64K pipe buffer and
                        # block right when we send "stop". Chunk-save OOMs
                        # surface here, after "Task finished".
                        while True:
                            rest = await proc.stdout.readline()
                            if not rest:
                                return
                            record(rest.decode("utf-8", errors="replace").rstrip())

                    # Must start before any settle sleep — during pre-gen the
                    # read_until loop was the drainer, but the settle path has
                    # no other stdout consumer.
                    drain = asyncio.create_task(drain_rest())

                    if not (chunky_loaded and PREGEN_RADIUS > 0):
                        logger.info("Server ready for job %s, letting chunks settle", job_id)
                        await asyncio.sleep(GENERATION_SETTLE)

                    if proc.returncode is None:
                        logger.info("Stopping server for job %s", job_id)
                        try:
                            proc.stdin.write(b"stop\n")
                            await proc.stdin.drain()
                        except (BrokenPipeError, ConnectionResetError):
                            # Stdout EOF can precede process reaping; the
                            # server is already dead and success reflects it.
                            logger.debug("Server pipe already closed for job %s", job_id)

                        try:
                            await asyncio.wait_for(proc.wait(), timeout=60)
                        except asyncio.TimeoutError:
                            logger.warning("Server didn't stop cleanly, terminating")
                            await _stop_proc(proc)

                    try:
                        await asyncio.wait_for(drain, timeout=10)
                    except asyncio.TimeoutError:
                        drain.cancel()
                        try:
                            # Let the cancellation land before log_file closes,
                            # or the task can write to a closed file.
                            await drain
                        except (asyncio.CancelledError, ValueError):
                            pass
                else:
                    logger.error("Server never became ready for job %s", job_id)
                    error = "Server exited before becoming ready (see server.log)"
                    await _stop_proc(proc)

        except asyncio.TimeoutError:
            logger.error("Server startup timed out for job %s", job_id)
            error = f"Server never became ready within {STARTUP_TIMEOUT}s"
        except Exception:
            logger.exception("Error during world generation for job %s", job_id)
            error = "Unexpected error during generation (see worker logs)"
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

        if fatal:
            # Region files written during an OOM are truncated; shipping them
            # would hand the user a silently corrupt world.
            logger.error(
                "Server ran out of memory for job %s — raise SERVER_MEMORY (currently %s) "
                "or lower PREGEN_RADIUS (currently %d)",
                job_id, SERVER_MEMORY, PREGEN_RADIUS,
            )
            success = False
            error = "Server ran out of memory (raise SERVER_MEMORY or lower PREGEN_RADIUS)"
    else:
        logger.warning(
            "No server jar found at %s — cannot generate for job %s",
            server_jar, job_id,
        )
        error = "Required jar missing: fabric-server-launch.jar"

    # rmtree/zip are sync disk I/O on a multi-hundred-MB tree; run them off
    # the event loop or every status poll and download stalls for minutes.
    def _rm_instance() -> None:
        shutil.rmtree(instance_dir, ignore_errors=True)

    if not success:
        await loop.run_in_executor(None, _rm_instance)
        _fail(job_id, error)
        logger.error("Generation failed for job %s: %s", job_id, error)
        return None

    try:
        zip_path = await loop.run_in_executor(None, _package_world, job_id, instance_dir)
    except Exception:
        # Without this a zip failure (e.g. disk full) strands the job in
        # "generating" until the next restart's reconcile pass.
        logger.exception("Failed to package world for job %s", job_id)
        await loop.run_in_executor(None, _rm_instance)
        _fail(job_id, "Failed to package world (disk full?)")
        return None

    await loop.run_in_executor(None, _rm_instance)

    _update_meta(job_id, {
        "status": "ready",
        "completed_at": datetime.now(timezone.utc).isoformat(),
        "world_zip": str(zip_path),
    })

    logger.info("Generation complete for job %s — zip at %s", job_id, zip_path)
    return zip_path
