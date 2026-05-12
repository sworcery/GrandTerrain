"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";

interface OrderStatus {
  orderId: string;
  status: string;
  createdAt: string;
}

const STATUS_LABELS: Record<string, string> = {
  pending: "Order Received",
  generating: "Generating World",
  review: "Under Review",
  ready: "Ready for Delivery",
  delivered: "Delivered",
};

export default function OrderStatusPage() {
  const params = useParams<{ id: string }>();
  const [order, setOrder] = useState<OrderStatus | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const url = `${process.env.NEXT_PUBLIC_WORKER_URL ?? "http://localhost:8080"}/api/orders/${params.id}`;
    fetch(url)
      .then((res) => {
        if (!res.ok) throw new Error("Order not found");
        return res.json();
      })
      .then(setOrder)
      .catch((e) => setError(e.message));
  }, [params.id]);

  if (error) {
    return (
      <div className="mx-auto max-w-2xl px-6 py-12">
        <h1 className="text-3xl font-bold text-red-400">Order Not Found</h1>
        <p className="mt-2 text-zinc-400">{error}</p>
      </div>
    );
  }

  if (!order) {
    return (
      <div className="mx-auto max-w-2xl px-6 py-12">
        <p className="text-zinc-400">Loading...</p>
      </div>
    );
  }

  const steps = ["pending", "generating", "review", "ready", "delivered"];
  const currentStep = steps.indexOf(order.status);

  return (
    <div className="mx-auto max-w-2xl px-6 py-12">
      <h1 className="text-3xl font-bold">Order Status</h1>
      <p className="mt-1 text-sm text-zinc-500">Order #{order.orderId}</p>

      <div className="mt-8 space-y-4">
        {steps.map((step, i) => (
          <div key={step} className="flex items-center gap-3">
            <div
              className={`h-3 w-3 rounded-full ${
                i <= currentStep ? "bg-emerald-500" : "bg-zinc-700"
              }`}
            />
            <span
              className={
                i <= currentStep ? "text-zinc-100" : "text-zinc-500"
              }
            >
              {STATUS_LABELS[step]}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
