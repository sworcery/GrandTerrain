"""Download the runtime jars the generator needs, if missing.

Runs once at container start (before uvicorn). Every URL can be overridden
via environment; files already present in DATA_DIR are left untouched, so a
mounted volume only downloads on first boot or after a deliberate delete.
"""
import logging
import os
import sys
import urllib.request
from pathlib import Path

logging.basicConfig(level=logging.INFO, format="%(asctime)s bootstrap %(levelname)s %(message)s")
logger = logging.getLogger("grandterrain.bootstrap")

DATA_DIR = Path(os.getenv("DATA_DIR", "/app/data"))

JARS = {
    Path(os.getenv("SERVER_JAR_PATH", str(DATA_DIR / "fabric-server-launch.jar"))): os.getenv(
        "SERVER_JAR_URL",
        "https://meta.fabricmc.net/v2/versions/loader/26.1.2/0.18.6/1.1.1/server/jar",
    ),
    Path(os.getenv("FABRIC_API_JAR_PATH", str(DATA_DIR / "fabric-api.jar"))): os.getenv(
        "FABRIC_API_JAR_URL",
        "https://cdn.modrinth.com/data/P7dR8mSH/versions/fm7UYECV/fabric-api-0.145.4%2B26.1.2.jar",
    ),
    Path(os.getenv("CHUNKY_JAR_PATH", str(DATA_DIR / "chunky.jar"))): os.getenv(
        "CHUNKY_JAR_URL",
        "https://cdn.modrinth.com/data/fALzjamp/versions/4Eotm6ov/Chunky-Fabric-1.5.3.jar",
    ),
    Path(os.getenv("MOD_JAR_PATH", str(DATA_DIR / "grandterrain.jar"))): os.getenv(
        "MOD_JAR_URL",
        "https://github.com/sworcery/GrandTerrain/releases/download/v0.1.8/grandterrain-0.1.8.jar",
    ),
}


def fetch(dest: Path, url: str) -> bool:
    if dest.exists() and dest.stat().st_size > 0:
        logger.info("%s already present (%d bytes)", dest.name, dest.stat().st_size)
        return True
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    try:
        logger.info("Downloading %s <- %s", dest.name, url)
        with urllib.request.urlopen(url, timeout=120) as resp, open(tmp, "wb") as out:
            while chunk := resp.read(1 << 16):
                out.write(chunk)
        size = tmp.stat().st_size
        with open(tmp, "rb") as fh:
            magic = fh.read(2)
        if size < 1024 or magic != b"PK":
            raise ValueError(f"downloaded file is not a jar ({size} bytes)")
        tmp.replace(dest)
        logger.info("Fetched %s (%d bytes)", dest.name, size)
        return True
    except Exception as e:
        logger.error("Failed to fetch %s: %s", dest.name, e)
        tmp.unlink(missing_ok=True)
        return False


def main() -> int:
    ok = all([fetch(dest, url) for dest, url in JARS.items()])
    if not ok:
        # Start anyway: the API stays reachable and jobs fail cleanly with a
        # logged reason rather than the container crash-looping.
        logger.error("Some jars are missing — generation jobs will fail until they exist.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
