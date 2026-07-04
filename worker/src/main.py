import asyncio
import logging
import os
import re
from contextlib import asynccontextmanager

from fastapi import BackgroundTasks, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

from .generator import WORLDS_DIR, generate_world
from .jobs import create_job, get_job
from .models import GenerateRequest, JobResponse

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(name)s %(levelname)s %(message)s",
)

ALLOWED_ORIGINS = os.getenv("CORS_ORIGINS", "*").split(",")
MAX_CONCURRENT_JOBS = int(os.getenv("MAX_CONCURRENT_JOBS", "1"))

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


@app.post("/api/generate", response_model=JobResponse)
async def generate(req: GenerateRequest, bg: BackgroundTasks):
    # _active_jobs is in-memory: correct only with a single uvicorn worker
    # (the Dockerfile CMD default). The check-then-increment has no await
    # between them, so it is race-free within one event loop.
    global _active_jobs
    if _active_jobs >= MAX_CONCURRENT_JOBS:
        raise HTTPException(429, "Server is busy generating another world. Try again shortly.")
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
