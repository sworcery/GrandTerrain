import json
import uuid
from datetime import datetime, timezone
from pathlib import Path

from .generator import ORDERS_DIR, ensure_dirs
from .models import OrderRequest, OrderResponse, WorldConfig


def create_order(req: OrderRequest) -> OrderResponse:
    ensure_dirs()
    order_id = uuid.uuid4().hex[:12]
    now = datetime.now(timezone.utc).isoformat()

    order_dir = ORDERS_DIR / order_id
    order_dir.mkdir(exist_ok=True)

    meta = {
        "order_id": order_id,
        "email": req.email,
        "status": "pending",
        "created_at": now,
        "notes": req.notes,
        "config": req.config.model_dump(),
    }
    (order_dir / "meta.json").write_text(json.dumps(meta, indent=2))

    return OrderResponse(order_id=order_id, status="pending", created_at=now)


def get_order(order_id: str) -> OrderResponse | None:
    meta_path = ORDERS_DIR / order_id / "meta.json"
    if not meta_path.exists():
        return None
    meta = json.loads(meta_path.read_text())
    return OrderResponse(
        order_id=meta["order_id"],
        status=meta["status"],
        created_at=meta["created_at"],
    )


def list_orders() -> list[dict]:
    ensure_dirs()
    orders = []
    for entry in sorted(ORDERS_DIR.iterdir(), reverse=True):
        meta_path = entry / "meta.json"
        if meta_path.exists():
            orders.append(json.loads(meta_path.read_text()))
    return orders


def update_order_status(order_id: str, status: str) -> bool:
    meta_path = ORDERS_DIR / order_id / "meta.json"
    if not meta_path.exists():
        return False
    meta = json.loads(meta_path.read_text())
    meta["status"] = status
    meta["updated_at"] = datetime.now(timezone.utc).isoformat()
    meta_path.write_text(json.dumps(meta, indent=2))
    return True
