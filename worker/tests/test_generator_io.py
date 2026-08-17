"""Packaging, sweep, and meta-write behaviour of the generator module."""
import json
import os
import time
import zipfile
from datetime import datetime, timedelta, timezone

import pytest

from src import generator
from src.generator import JOBS_DIR, WORLDS_DIR, _package_world, _update_meta, sweep_old_jobs
from src.jobs import get_job
from src.models import JobResponse


def _make_world(tmp_path):
    instance = tmp_path / "server"
    (instance / "world" / "region").mkdir(parents=True)
    (instance / "world" / "level.dat").write_bytes(b"\x00" * 64)
    (instance / "world" / "region" / "r.0.0.mca").write_bytes(b"\x01" * 4096)
    return instance


def test_package_world_writes_final_zip_and_no_part(tmp_path):
    generator.ensure_dirs()
    instance = _make_world(tmp_path)
    zip_path = _package_world("aaaaaaaaaaaa", instance)
    assert zip_path == WORLDS_DIR / "aaaaaaaaaaaa.zip"
    assert zip_path.exists()
    assert not (WORLDS_DIR / "aaaaaaaaaaaa.zip.part").exists()
    with zipfile.ZipFile(zip_path) as zf:
        names = set(zf.namelist())
        assert "world/level.dat" in names
        assert "world/region/r.0.0.mca" in names
        # Region files are pre-compressed; storing avoids a 5-10x CPU tax.
        assert all(i.compress_type == zipfile.ZIP_STORED for i in zf.infolist())


def test_package_world_cleans_part_on_failure(tmp_path, monkeypatch):
    generator.ensure_dirs()
    instance = _make_world(tmp_path)

    class Boom(zipfile.ZipFile):
        def write(self, *a, **kw):
            raise OSError(28, "No space left on device")

    monkeypatch.setattr(generator.zipfile, "ZipFile", Boom)
    with pytest.raises(OSError):
        _package_world("bbbbbbbbbbbb", instance)
    assert not (WORLDS_DIR / "bbbbbbbbbbbb.zip").exists()
    assert not (WORLDS_DIR / "bbbbbbbbbbbb.zip.part").exists()


def _write_meta(job_id: str, status: str, created_at: str | None) -> None:
    d = JOBS_DIR / job_id
    d.mkdir(parents=True, exist_ok=True)
    meta = {"job_id": job_id, "status": status}
    if created_at:
        meta["created_at"] = created_at
    (d / "meta.json").write_text(json.dumps(meta))


def _old_iso() -> str:
    return (datetime.now(timezone.utc) - timedelta(hours=generator.RETENTION_HOURS + 5)).isoformat()


def test_sweep_skips_generating_job_but_removes_expired_ready_job():
    generator.ensure_dirs()
    _write_meta("cccccccccccc", "generating", _old_iso())
    _write_meta("dddddddddddd", "ready", _old_iso())
    (WORLDS_DIR / "cccccccccccc.zip").write_bytes(b"PK")
    (WORLDS_DIR / "dddddddddddd.zip").write_bytes(b"PK")
    # Make the on-disk mtimes look ancient too, so the sweep can only be
    # keeping the generating job because of its status.
    ancient = time.time() - (generator.RETENTION_HOURS + 5) * 3600
    for p in (JOBS_DIR / "cccccccccccc", WORLDS_DIR / "cccccccccccc.zip"):
        os.utime(p, (ancient, ancient))

    sweep_old_jobs()

    assert (JOBS_DIR / "cccccccccccc").exists()
    assert (WORLDS_DIR / "cccccccccccc.zip").exists()
    assert not (JOBS_DIR / "dddddddddddd").exists()
    assert not (WORLDS_DIR / "dddddddddddd.zip").exists()


def test_sweep_uses_created_at_not_mtime():
    generator.ensure_dirs()
    # Fresh by created_at, but the dir mtime is ancient (e.g. restored volume).
    _write_meta("eeeeeeeeeeee", "ready", datetime.now(timezone.utc).isoformat())
    ancient = time.time() - (generator.RETENTION_HOURS + 5) * 3600
    os.utime(JOBS_DIR / "eeeeeeeeeeee", (ancient, ancient))
    sweep_old_jobs()
    assert (JOBS_DIR / "eeeeeeeeeeee").exists()


def test_update_meta_survives_missing_job_dir():
    # A swept job dir must not raise out of the background task.
    _update_meta("ffffffffffff", {"status": "failed"})
    assert not (JOBS_DIR / "ffffffffffff").exists()


def test_update_meta_is_atomic_and_carries_error():
    generator.ensure_dirs()
    _write_meta("abcabcabcabc", "generating", datetime.now(timezone.utc).isoformat())
    _update_meta("abcabcabcabc", {"status": "failed", "error": "Failed to package world"})
    assert not (JOBS_DIR / "abcabcabcabc" / "meta.json.tmp").exists()
    job = get_job("abcabcabcabc")
    assert job.status == "failed"
    assert job.error == "Failed to package world"


def test_get_job_none_on_corrupt_meta():
    generator.ensure_dirs()
    d = JOBS_DIR / "010101010101"
    d.mkdir(parents=True, exist_ok=True)
    (d / "meta.json").write_text("{corrupt")
    assert get_job("010101010101") is None
    assert get_job("nonexistent0") is None


def test_job_response_error_field_camel_case():
    r = JobResponse(job_id="x", status="failed", created_at="", error="boom")
    assert r.model_dump(by_alias=True)["error"] == "boom"
    assert JobResponse(job_id="x", status="ready", created_at="").error is None
