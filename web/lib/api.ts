import type { WorldConfig } from "./config-schema";

const WORKER_URL =
  process.env.NEXT_PUBLIC_WORKER_URL ?? "http://localhost:8080";

export interface OrderRequest {
  email: string;
  config: WorldConfig;
  notes?: string;
}

export interface OrderResponse {
  orderId: string;
  status: "pending" | "generating" | "review" | "ready" | "delivered";
  createdAt: string;
}

export async function submitOrder(
  order: OrderRequest,
): Promise<OrderResponse> {
  const res = await fetch(`${WORKER_URL}/api/orders`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(order),
  });
  if (!res.ok) throw new Error(`Order submission failed: ${res.status}`);
  return res.json();
}

export async function getOrderStatus(
  orderId: string,
): Promise<OrderResponse> {
  const res = await fetch(`${WORKER_URL}/api/orders/${orderId}`);
  if (!res.ok) throw new Error(`Order lookup failed: ${res.status}`);
  return res.json();
}
