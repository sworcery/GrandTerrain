import json
import uuid
from datetime import datetime, timezone

from .generator import JOBS_DIR, ensure_dirs
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
    (job_dir / "meta.json").write_text(json.dumps(meta, indent=2))

    return JobResponse(job_id=job_id, status="pending", created_at=now)


def get_job(job_id: str) -> JobResponse | None:
    meta_path = JOBS_DIR / job_id / "meta.json"
    if not meta_path.exists():
        return None
    meta = json.loads(meta_path.read_text())
    return JobResponse(
        job_id=meta["job_id"],
        status=meta["status"],
        created_at=meta["created_at"],
    )
