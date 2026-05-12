import os
from contextlib import asynccontextmanager

from fastapi import BackgroundTasks, Depends, FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates

from .generator import generate_world
from .models import OrderRequest, OrderResponse, WorldConfig
from .orders import create_order, get_order, list_orders, update_order_status

ADMIN_PASSWORD = os.getenv("ADMIN_PASSWORD", "changeme")


@asynccontextmanager
async def lifespan(app: FastAPI):
    from .generator import ensure_dirs
    ensure_dirs()
    yield

app = FastAPI(title="GrandTerrain Worker", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

templates = Jinja2Templates(directory="templates")


# --- Customer API ---

@app.post("/api/orders", response_model=OrderResponse)
async def submit_order(req: OrderRequest, bg: BackgroundTasks):
    order = create_order(req)
    bg.add_task(generate_world, order.order_id, req.config)
    return order


@app.get("/api/orders/{order_id}", response_model=OrderResponse)
async def order_status(order_id: str):
    order = get_order(order_id)
    if not order:
        raise HTTPException(404, "Order not found")
    return order


# --- Admin API ---

def verify_admin(request: Request):
    auth = request.headers.get("Authorization", "")
    if auth != f"Bearer {ADMIN_PASSWORD}":
        raise HTTPException(401, "Unauthorized")


@app.get("/admin", response_class=HTMLResponse)
async def admin_dashboard(request: Request):
    orders = list_orders()
    return templates.TemplateResponse("admin/dashboard.html", {
        "request": request,
        "orders": orders,
        "counts": {
            "pending": sum(1 for o in orders if o["status"] == "pending"),
            "generating": sum(1 for o in orders if o["status"] == "generating"),
            "review": sum(1 for o in orders if o["status"] == "review"),
            "ready": sum(1 for o in orders if o["status"] == "ready"),
            "delivered": sum(1 for o in orders if o["status"] == "delivered"),
        },
    })


@app.get("/admin/orders", response_class=HTMLResponse)
async def admin_orders(request: Request):
    orders = list_orders()
    return templates.TemplateResponse("admin/orders.html", {
        "request": request,
        "orders": orders,
    })


@app.post("/admin/orders/{order_id}/status")
async def admin_update_status(order_id: str, status: str, _=Depends(verify_admin)):
    if status not in ("pending", "generating", "review", "ready", "delivered"):
        raise HTTPException(400, "Invalid status")
    if not update_order_status(order_id, status):
        raise HTTPException(404, "Order not found")
    return {"ok": True}


@app.get("/admin/generate", response_class=HTMLResponse)
async def admin_generate_page(request: Request):
    return templates.TemplateResponse("admin/generate.html", {
        "request": request,
    })


@app.post("/admin/generate")
async def admin_generate(req: OrderRequest, bg: BackgroundTasks, _=Depends(verify_admin)):
    order = create_order(req)
    bg.add_task(generate_world, order.order_id, req.config)
    return order
