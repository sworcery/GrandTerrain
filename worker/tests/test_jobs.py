import json

from src.generator import JOBS_DIR
from src.jobs import create_job, get_job
from src.models import GenerateRequest, WorldConfig


def _request() -> GenerateRequest:
    return GenerateRequest(config=WorldConfig())


def test_create_job_writes_pending_meta():
    job = create_job(_request())
    meta = json.loads((JOBS_DIR / job.job_id / "meta.json").read_text())
    assert meta["status"] == "pending"
    assert meta["job_id"] == job.job_id
    assert "config" in meta


def test_get_job_roundtrip():
    job = create_job(_request())
    fetched = get_job(job.job_id)
    assert fetched is not None
    assert fetched.job_id == job.job_id
    assert fetched.status == "pending"
    assert fetched.created_at == job.created_at


def test_get_job_missing_returns_none():
    assert get_job("000000000000") is None


def test_get_job_tolerates_incomplete_meta():
    # A retention sweep or partial write can leave stripped metas; a status
    # poll must degrade to "failed", not crash with KeyError.
    job_dir = JOBS_DIR / "deadbeef0000"
    job_dir.mkdir(parents=True, exist_ok=True)
    (job_dir / "meta.json").write_text("{}")
    fetched = get_job("deadbeef0000")
    assert fetched is not None
    assert fetched.status == "failed"
    assert fetched.job_id == "deadbeef0000"
