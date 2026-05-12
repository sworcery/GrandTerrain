"use client";

import { useState } from "react";
import { ConfigPanel } from "@/components/configurator/config-panel";
import {
  DEFAULT_CONFIG,
  TERRAIN_FIELDS,
  CAVE_FIELDS,
  RIVER_FIELDS,
  CLIMATE_FIELDS,
  BIOME_THRESHOLD_FIELDS,
  STRUCTURE_FIELDS,
  type WorldConfig,
} from "@/lib/config-schema";

type Category = keyof WorldConfig;

const SECTIONS: { key: Category; title: string; fields: typeof TERRAIN_FIELDS }[] = [
  { key: "terrain", title: "Terrain", fields: TERRAIN_FIELDS },
  { key: "caves", title: "Caves", fields: CAVE_FIELDS },
  { key: "rivers", title: "Rivers", fields: RIVER_FIELDS },
  { key: "climate", title: "Snow & Climate", fields: CLIMATE_FIELDS },
  { key: "biomeThresholds", title: "Biome Thresholds", fields: BIOME_THRESHOLD_FIELDS },
  { key: "structures", title: "Structures", fields: STRUCTURE_FIELDS },
];

export default function ConfigurePage() {
  const [config, setConfig] = useState<WorldConfig>(structuredClone(DEFAULT_CONFIG));
  const [email, setEmail] = useState("");
  const [notes, setNotes] = useState("");

  function updateField(category: Category, key: string, value: number | boolean) {
    setConfig((prev) => ({
      ...prev,
      [category]: { ...prev[category], [key]: value },
    }));
  }

  function resetAll() {
    setConfig(structuredClone(DEFAULT_CONFIG));
  }

  async function handleSubmit() {
    if (!email) return;
    const res = await fetch(
      `${process.env.NEXT_PUBLIC_WORKER_URL ?? "http://localhost:8080"}/api/orders`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email, config, notes: notes || undefined }),
      },
    );
    if (res.ok) {
      const data = await res.json();
      window.location.href = `/orders/${data.orderId}`;
    }
  }

  return (
    <div className="mx-auto max-w-5xl px-6 py-12">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Configure Your World</h1>
          <p className="mt-1 text-zinc-400">
            Adjust parameters across 6 categories. Every world is reviewed before delivery.
          </p>
        </div>
        <button
          onClick={resetAll}
          className="rounded-lg border border-zinc-700 px-4 py-2 text-sm text-zinc-400 hover:border-zinc-500 hover:text-zinc-200"
        >
          Reset to Defaults
        </button>
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {SECTIONS.map((section) => (
          <ConfigPanel
            key={section.key}
            title={section.title}
            fields={section.fields}
            values={config[section.key] as Record<string, number | boolean>}
            onChange={(key, value) => updateField(section.key, key, value)}
          />
        ))}
      </div>

      <div className="mt-10 rounded-xl border border-zinc-800 p-6">
        <h3 className="mb-4 text-lg font-semibold">Place Your Order</h3>
        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-sm text-zinc-400">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              className="w-full rounded-lg border border-zinc-700 bg-zinc-900 px-4 py-2 text-zinc-100 placeholder:text-zinc-600 focus:border-emerald-500 focus:outline-none"
            />
          </div>
          <div>
            <label className="mb-1 block text-sm text-zinc-400">
              Notes (optional)
            </label>
            <input
              type="text"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Any special requests..."
              className="w-full rounded-lg border border-zinc-700 bg-zinc-900 px-4 py-2 text-zinc-100 placeholder:text-zinc-600 focus:border-emerald-500 focus:outline-none"
            />
          </div>
        </div>
        <button
          onClick={handleSubmit}
          disabled={!email}
          className="mt-4 rounded-lg bg-emerald-600 px-8 py-3 font-medium text-white hover:bg-emerald-500 disabled:opacity-40 disabled:hover:bg-emerald-600"
        >
          Submit Order
        </button>
      </div>
    </div>
  );
}
