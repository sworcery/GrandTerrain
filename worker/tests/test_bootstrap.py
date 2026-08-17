from pathlib import Path

import pytest

from src import bootstrap
from src.bootstrap import fetch


@pytest.fixture(autouse=True)
def _no_backoff(monkeypatch):
    monkeypatch.setattr(bootstrap, "BACKOFF", (0, 0, 0))


def test_fetch_retries_then_succeeds(tmp_path, monkeypatch):
    src = _fake_jar(tmp_path / "src.jar")
    dest = tmp_path / "dest.jar"
    real = bootstrap.urllib.request.urlopen
    calls = []

    def flaky(url, timeout=None):
        calls.append(timeout)
        if len(calls) < 3:
            raise OSError("connection reset")
        return real(url, timeout=timeout)

    monkeypatch.setattr(bootstrap.urllib.request, "urlopen", flaky)
    assert fetch(dest, src.as_uri()) is True
    assert len(calls) == 3
    assert all(t == 20 for t in calls)


def test_fetch_gives_up_after_three_attempts(tmp_path, monkeypatch):
    calls = []

    def dead(url, timeout=None):
        calls.append(url)
        raise OSError("no route to host")

    monkeypatch.setattr(bootstrap.urllib.request, "urlopen", dead)
    assert fetch(tmp_path / "dest.jar", "https://example.invalid/x.jar") is False
    assert len(calls) == 3


def _fake_jar(path: Path, magic: bytes = b"PK") -> Path:
    path.write_bytes(magic + b"\x03\x04" + b"\0" * 2048)
    return path


def test_fetch_downloads_valid_jar(tmp_path):
    src = _fake_jar(tmp_path / "src.jar")
    dest = tmp_path / "out" / "dest.jar"
    assert fetch(dest, src.as_uri()) is True
    assert dest.exists()
    assert dest.read_bytes()[:2] == b"PK"
    assert not dest.with_suffix(".jar.part").exists()


def test_fetch_rejects_non_jar_content(tmp_path):
    src = tmp_path / "src.jar"
    src.write_bytes(b"<html>error page</html>" + b"\0" * 2048)
    dest = tmp_path / "dest.jar"
    assert fetch(dest, src.as_uri()) is False
    assert not dest.exists()
    assert not dest.with_suffix(".jar.part").exists()


def test_fetch_rejects_tiny_file(tmp_path):
    src = tmp_path / "src.jar"
    src.write_bytes(b"PK\x03\x04")  # jar magic but < 1KB
    dest = tmp_path / "dest.jar"
    assert fetch(dest, src.as_uri()) is False
    assert not dest.exists()


def test_fetch_skips_existing_file(tmp_path):
    src = _fake_jar(tmp_path / "src.jar")
    dest = _fake_jar(tmp_path / "dest.jar", b"PK")
    before = dest.read_bytes()
    assert fetch(dest, "file:///nonexistent/never-touched.jar") is True
    assert dest.read_bytes() == before  # untouched, no download attempted


def test_fetch_failure_leaves_no_partial(tmp_path):
    dest = tmp_path / "dest.jar"
    assert fetch(dest, "file:///nonexistent/missing.jar") is False
    assert not dest.exists()
    assert not dest.with_suffix(".jar.part").exists()
