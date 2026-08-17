import asyncio
import logging
import os
import re
import shutil
from contextlib import asynccontextmanager

from fastapi import BackgroundTasks, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

from .generator import (
    CHUNKY_JAR,
    DATA_DIR,
    FABRIC_API_JAR,
    MOD_JAR,
    SERVER_JAR,
    WORLDS_DIR,
    generate_world,
)
from .jobs import create_job, get_job
from .models import GenerateRequest, JobResponse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(name)s %(levelname)s %(message)s",
)

ALLOWED_ORIGINS = os.getenv("CORS_ORIGINS", "*").split(",")
MAX_CONCURRENT_JOBS = int(os.getenv("MAX_CONCURRENT_JOBS", "1"))
# Refuse new jobs below this much free space: a world zip is hundreds of MB
# and the server instance more, and a full disk takes the whole service down.
MIN_FREE_DISK_GB = float(os.getenv("MIN_FREE_DISK_GB", "5"))

_JOB_ID_RE = re.compile(r"^[0-9a-f]{12}$")
_active_jobs = 0


@asynccontextmanager
async def lifespan(app: FastAPI):
    from .generator import ensure_dirs, live_procs, reconcile_stale_jobs, sweep_old_jobs
    ensure_dirs()
    reconcile_stale_jobs()
    sweep_old_jobs()
    yield
    # Kill any in-flight generation servers; task cancellation alone would
    # orphan the JVMs during the graceful-shutdown window.
    for proc in list(live_procs):
        if proc.returncode is None:
            proc.kill()


app = FastAPI(title="GrandTerrain Worker", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_methods=["*"],
    allow_headers=["*"],
)


async def _run_job(job_id: str, config) -> None:
    global _active_jobs
    try:
        await generate_world(job_id, config)
    finally:
        _active_jobs -= 1


def _valid_job_id(job_id: str) -> bool:
    return bool(_JOB_ID_RE.match(job_id))


def _jar_status() -> dict[str, bool]:
    return {
        "grandterrain": MOD_JAR.exists(),
        "fabric_api": FABRIC_API_JAR.exists(),
        "server": SERVER_JAR.exists(),
        "chunky": CHUNKY_JAR.exists(),
    }


@app.get("/")
async def root():
    return {"service": "GrandTerrain worker", "docs": "/docs", "health": "/health"}


@app.get("/health")
async def health():
    jars = _jar_status()
    # The mod and server jars are the hard requirements; missing Fabric API
    # or Chunky degrades output but generation still runs.
    ok = jars["grandterrain"] and jars["server"]
    return {
        "status": "ok" if ok else "degraded",
        "jars": jars,
        "active_jobs": _active_jobs,
    }


@app.post("/api/generate", response_model=JobResponse)
async def generate(req: GenerateRequest, bg: BackgroundTasks):
    # _active_jobs is in-memory: correct only with a single uvicorn worker
    # (the Dockerfile CMD default). The check-then-increment has no await
    # between them, so it is race-free within one event loop.
    global _active_jobs
    if _active_jobs >= MAX_CONCURRENT_JOBS:
        raise HTTPException(429, "Server is busy generating another world. Try again shortly.")
    # Fail fast with a real reason instead of creating a job that dies in
    # the background: bootstrap starts the API even if a download failed.
    for name, jar in (("grandterrain.jar", MOD_JAR), ("fabric-server-launch.jar", SERVER_JAR)):
        if not jar.exists():
            raise HTTPException(
                503,
                f"Worker not provisioned: {name} missing — check container logs "
                "(bootstrap download failed?)",
            )
    free_gb = shutil.disk_usage(DATA_DIR).free / 1e9
    if free_gb < MIN_FREE_DISK_GB:
        raise HTTPException(
            503,
            f"Insufficient disk: {free_gb:.1f} GB free, need at least {MIN_FREE_DISK_GB:g} GB",
        )
    from .generator import sweep_old_jobs
    # Off the event loop: deleting an expired multi-hundred-MB zip inline
    # would stall every concurrent status poll.
    asyncio.get_running_loop().run_in_executor(None, sweep_old_jobs)
    # create_job is fallible (disk); take the slot only after it succeeds,
    # otherwise a single failure leaks the slot and 429s everyone forever.
    job = create_job(req)
    _active_jobs += 1
    try:
        bg.add_task(_run_job, job.job_id, req.config)
    except Exception:
        _active_jobs -= 1
        raise
    return job


@app.get("/api/jobs/{job_id}", response_model=JobResponse)
async def job_status(job_id: str):
    if not _valid_job_id(job_id):
        raise HTTPException(404, "Job not found")
    job = get_job(job_id)
    if not job:
        raise HTTPException(404, "Job not found")
    return job


@app.get("/api/jobs/{job_id}/download")
async def download_world(job_id: str):
    if not _valid_job_id(job_id):
        raise HTTPException(404, "World file not found.")
    zip_path = WORLDS_DIR / f"{job_id}.zip"
    if not zip_path.exists():
        raise HTTPException(404, "World file not found. It may still be generating.")
    return FileResponse(
        path=str(zip_path),
        media_type="application/zip",
        filename=f"grandterrain-{job_id}.zip",
    )
