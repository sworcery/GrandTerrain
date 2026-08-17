import json
import uuid
from datetime import datetime, timezone

from .generator import JOBS_DIR, ensure_dirs, write_meta_atomic
from .models import GenerateRequest, JobResponse


def create_job(req: GenerateRequest) -> JobResponse:
    ensure_dirs()
    job_id = uuid.uuid4().hex[:12]
    now = datetime.now(timezone.utc).isoformat()

    job_dir = JOBS_DIR / job_id
    job_dir.mkdir(exist_ok=True)

    meta = {
        "job_id": job_id,
        "status": "pending",
        "created_at": now,
        "config": req.config.model_dump(),
    }
    write_meta_atomic(job_dir / "meta.json", meta)

    return JobResponse(job_id=job_id, status="pending", created_at=now)


def get_job(job_id: str) -> JobResponse | None:
    meta_path = JOBS_DIR / job_id / "meta.json"
    try:
        meta = json.loads(meta_path.read_text())
    except (FileNotFoundError, ValueError, OSError):
        # A retention sweep racing a status poll, or a corrupt meta, must
        # surface as 404 (which the web client handles), not a 500.
        return None
    if not isinstance(meta, dict):
        return None
    # .get guards: a retention sweep or partial write can leave incomplete meta;
    # a status poll must degrade to "failed", not crash with KeyError.
    return JobResponse(
        job_id=meta.get("job_id", job_id),
        status=meta.get("status", "failed"),
        created_at=meta.get("created_at", ""),
        error=meta.get("error"),
    )
