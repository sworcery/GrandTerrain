import asyncio
import json
import os
import shutil
from datetime import datetime, timezone
from pathlib import Path

from .models import WorldConfig

DATA_DIR = Path(os.getenv("DATA_DIR", "/app/data"))
ORDERS_DIR = DATA_DIR / "orders"
WORLDS_DIR = DATA_DIR / "worlds"
MOD_JAR = Path(os.getenv("MOD_JAR_PATH", "/app/data/grandterrain.jar"))


def ensure_dirs() -> None:
    ORDERS_DIR.mkdir(parents=True, exist_ok=True)
    WORLDS_DIR.mkdir(parents=True, exist_ok=True)


def write_config_properties(order_id: str, config: WorldConfig) -> Path:
    order_dir = ORDERS_DIR / order_id
    order_dir.mkdir(exist_ok=True)
    props_path = order_dir / "grandterrain.properties"
    props_path.write_text(config.to_properties())
    return props_path


async def generate_world(order_id: str, config: WorldConfig) -> Path:
    """
    Generate a Minecraft world using a headless Fabric server with the
    GrandTerrain mod. The config is written as a properties file that
    the mod reads on startup.

    Steps:
    1. Write grandterrain.properties for this order's config
    2. Set up a temporary server directory with the mod jar
    3. Launch headless Fabric server to generate chunks
    4. Use Chunky to pre-render (optional, future)
    5. Package the world save as a zip

    This is a stub — the actual server invocation will be wired up
    once the mod has been tested in-game.
    """
    ensure_dirs()
    order_dir = ORDERS_DIR / order_id
    order_dir.mkdir(exist_ok=True)

    write_config_properties(order_id, config)

    meta = {
        "order_id": order_id,
        "status": "generating",
        "started_at": datetime.now(timezone.utc).isoformat(),
        "config": config.model_dump(),
    }
    (order_dir / "meta.json").write_text(json.dumps(meta, indent=2))

    # TODO: actual server launch
    # server_dir = order_dir / "server"
    # server_dir.mkdir(exist_ok=True)
    # shutil.copy(MOD_JAR, server_dir / "mods" / "grandterrain.jar")
    # proc = await asyncio.create_subprocess_exec(
    #     "java", "-jar", "fabric-server.jar", "--nogui",
    #     cwd=str(server_dir),
    # )
    # await proc.wait()

    world_dir = WORLDS_DIR / order_id
    world_dir.mkdir(exist_ok=True)
    (world_dir / ".placeholder").write_text("World files will be generated here.\n")

    meta["status"] = "review"
    meta["completed_at"] = datetime.now(timezone.utc).isoformat()
    (order_dir / "meta.json").write_text(json.dumps(meta, indent=2))

    return world_dir
