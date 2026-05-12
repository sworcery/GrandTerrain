"use client";

export default function CheckoutPage() {
  return (
    <div className="mx-auto max-w-2xl px-6 py-12">
      <h1 className="text-3xl font-bold">Checkout</h1>
      <p className="mt-2 text-zinc-400">
        Stripe payment integration will be added here.
      </p>
      <div className="mt-8 rounded-xl border border-zinc-800 p-6 text-center text-zinc-500">
        Payment form placeholder — Stripe Elements will mount here.
      </div>
    </div>
  );
}
