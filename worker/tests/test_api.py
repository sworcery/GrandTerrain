"""HTTP-level tests against the FastAPI app (jars are absent in the test DATA_DIR)."""
import json

import pytest
from fastapi.testclient import TestClient

from src import main
from src.generator import JOBS_DIR


@pytest.fixture
def client():
    with TestClient(main.app) as c:
        yield c


def _generate_body():
    return {"config": {}}


def test_root_landing(client):
    r = client.get("/")
    assert r.status_code == 200
    assert r.json() == {"service": "GrandTerrain worker", "docs": "/docs", "health": "/health"}


def test_health_shape_degraded_without_jars(client):
    r = client.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "degraded"
    assert set(body["jars"]) == {"grandterrain", "fabric_api", "server", "chunky"}
    assert all(isinstance(v, bool) for v in body["jars"].values())
    assert body["active_jobs"] == 0


def test_health_ok_when_required_jars_present(client, monkeypatch, tmp_path):
    mod = tmp_path / "grandterrain.jar"
    server = tmp_path / "server.jar"
    mod.write_bytes(b"PK")
    server.write_bytes(b"PK")
    monkeypatch.setattr(main, "MOD_JAR", mod)
    monkeypatch.setattr(main, "SERVER_JAR", server)
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert body["jars"]["grandterrain"] is True
    assert body["jars"]["server"] is True


def test_generate_503_when_jars_missing(client):
    before = {p.name for p in JOBS_DIR.iterdir()}
    r = client.post("/api/generate", json=_generate_body())
    assert r.status_code == 503
    assert "not provisioned" in r.json()["detail"]
    assert "grandterrain.jar" in r.json()["detail"]
    # A refused request must not leave a job dir behind.
    assert {p.name for p in JOBS_DIR.iterdir()} == before


def test_generate_503_when_disk_low(client, monkeypatch, tmp_path):
    mod = tmp_path / "grandterrain.jar"
    server = tmp_path / "server.jar"
    mod.write_bytes(b"PK")
    server.write_bytes(b"PK")
    monkeypatch.setattr(main, "MOD_JAR", mod)
    monkeypatch.setattr(main, "SERVER_JAR", server)
    monkeypatch.setattr(main, "MIN_FREE_DISK_GB", 1e9)  # nobody has an exabyte free
    r = client.post("/api/generate", json=_generate_body())
    assert r.status_code == 503
    assert r.json()["detail"].startswith("Insufficient disk:")


def test_job_status_404_on_missing_and_corrupt(client):
    assert client.get("/api/jobs/000000000000").status_code == 404
    job_dir = JOBS_DIR / "badbadbadbad"
    job_dir.mkdir(parents=True, exist_ok=True)
    (job_dir / "meta.json").write_text("{not json")
    assert client.get("/api/jobs/badbadbadbad").status_code == 404


def test_job_status_exposes_error(client):
    job_dir = JOBS_DIR / "feedfeedfeed"
    job_dir.mkdir(parents=True, exist_ok=True)
    (job_dir / "meta.json").write_text(json.dumps({
        "job_id": "feedfeedfeed",
        "status": "failed",
        "created_at": "2026-01-01T00:00:00+00:00",
        "error": "Server ran out of memory (raise SERVER_MEMORY or lower PREGEN_RADIUS)",
    }))
    body = client.get("/api/jobs/feedfeedfeed").json()
    assert body["status"] == "failed"
    assert body["error"].startswith("Server ran out of memory")


def test_download_supports_head(client):
    """The web UI HEAD-checks the download before navigating. FastAPI's
    APIRoute does not add HEAD alongside GET, so without an explicit
    api_route every download 405s and the UI reports the world as gone."""
    from src.generator import WORLDS_DIR

    job_id = "abcdef123456"
    WORLDS_DIR.mkdir(parents=True, exist_ok=True)
    (WORLDS_DIR / f"{job_id}.zip").write_bytes(b"PK\x03\x04" + b"\0" * 512)
    try:
        head = client.head(f"/api/jobs/{job_id}/download")
        assert head.status_code == 200, f"HEAD must be allowed, got {head.status_code}"
        assert client.get(f"/api/jobs/{job_id}/download").status_code == 200
    finally:
        (WORLDS_DIR / f"{job_id}.zip").unlink(missing_ok=True)
