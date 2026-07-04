"use client";

import { useEffect, useRef, useState } from "react";
import { ConfigPanel } from "@/components/configurator/config-panel";
import {
  DEFAULT_CONFIG,
  TERRAIN_FIELDS,
  CAVE_FIELDS,
  RIVER_FIELDS,
  CLIMATE_FIELDS,
  BIOME_THRESHOLD_FIELDS,
  STRUCTURE_FIELDS,
  sanitizeConfig,
  type WorldConfig,
} from "@/lib/config-schema";
import { PRESETS } from "@/lib/presets";
import { generateWorld, getJobStatus, downloadUrl } from "@/lib/api";

type Category = keyof WorldConfig;

const SECTIONS: { key: Category; title: string; fields: typeof TERRAIN_FIELDS }[] = [
  { key: "terrain", title: "Terrain", fields: TERRAIN_FIELDS },
  { key: "caves", title: "Caves", fields: CAVE_FIELDS },
  { key: "rivers", title: "Rivers", fields: RIVER_FIELDS },
  { key: "climate", title: "Snow & Climate", fields: CLIMATE_FIELDS },
  { key: "biomeThresholds", title: "Biome Thresholds", fields: BIOME_THRESHOLD_FIELDS },
  { key: "structures", title: "Structures", fields: STRUCTURE_FIELDS },
];

const ACTIVE_JOB_KEY = "gt_active_job";

export default function ConfigurePage() {
  const [config, setConfig] = useState<WorldConfig>(structuredClone(DEFAULT_CONFIG));
  const [activePreset, setActivePreset] = useState("default");
  const [generating, setGenerating] = useState(false);
  const [genStatus, setGenStatus] = useState<string | null>(null);
  // Ref guard: `disabled={generating}` alone lets a fast double-click fire
  // two POSTs before the state update flushes.
  const submittingRef = useRef(false);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    // Resume a job that survived a refresh — generation runs server-side
    // regardless of the browser, and losing the id strands a finished world.
    const stored = localStorage.getItem(ACTIVE_JOB_KEY);
    if (stored && !submittingRef.current) {
      submittingRef.current = true;
      setGenerating(true);
      setGenStatus(`Resuming job ${stored}…`);
      void pollUntilDone(stored);
    }
    return () => {
      mountedRef.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  function updateField(category: Category, key: string, value: number | boolean) {
    setActivePreset("custom");
    setConfig((prev) => ({
      ...prev,
      [category]: { ...prev[category], [key]: value },
    }));
  }

  function applyPreset(presetId: string) {
    const preset = PRESETS.find((p) => p.id === presetId);
    if (preset) {
      setConfig(structuredClone(preset.config));
      setActivePreset(presetId);
    }
  }

  async function pollUntilDone(jobId: string) {
    try {
      let consecutiveFailures = 0;
      for (let attempt = 0; attempt < 150; attempt++) {
        await new Promise((r) => setTimeout(r, 4000));
        // Page left — stop polling and never fire a late redirect.
        if (!mountedRef.current) return;
        let status;
        try {
          status = await getJobStatus(jobId);
          consecutiveFailures = 0;
        } catch {
          // One network blip in a 10-minute wait must not abort the job.
          if (++consecutiveFailures >= 5) {
            throw new Error("Lost contact with the generation server. Reload this page to resume.");
          }
          continue;
        }
        if (status === null) {
          // Job is definitively gone (swept or worker reset) — clear the stored
          // id so the next page load doesn't retry a dead job forever.
          localStorage.removeItem(ACTIVE_JOB_KEY);
          if (mountedRef.current) setGenStatus("This job no longer exists on the server. Please generate again.");
          return;
        }
        if (status.status === "ready") {
          localStorage.removeItem(ACTIVE_JOB_KEY);
          if (!mountedRef.current) return;
          setGenStatus("Done — downloading your world file.");
          window.location.href = downloadUrl(jobId);
          return;
        }
        if (status.status === "failed") {
          localStorage.removeItem(ACTIVE_JOB_KEY);
          if (mountedRef.current) setGenStatus("Generation failed. Please try again.");
          return;
        }
      }
      if (mountedRef.current) {
        setGenStatus(`Still generating (job ${jobId}) — reload this page later to resume.`);
      }
    } catch (e) {
      if (mountedRef.current) {
        setGenStatus(e instanceof Error ? e.message : "Generation failed.");
      }
    } finally {
      submittingRef.current = false;
      if (mountedRef.current) setGenerating(false);
    }
  }

  async function handleGenerate() {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setGenerating(true);
    setGenStatus("Submitting…");
    try {
      const job = await generateWorld(config);
      localStorage.setItem(ACTIVE_JOB_KEY, job.jobId);
      setGenStatus(`Generating your world (job ${job.jobId}) — this can take a few minutes…`);
      await pollUntilDone(job.jobId);
    } catch (e) {
      submittingRef.current = false;
      setGenerating(false);
      setGenStatus(e instanceof Error ? e.message : "Generation request failed.");
    }
  }

  function exportConfig() {
    const blob = new Blob([JSON.stringify(config, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "grandterrain-config.json";
    a.click();
    URL.revokeObjectURL(url);
  }

  function importConfig() {
    const input = document.createElement("input");
    input.type = "file";
    input.accept = ".json";
    input.onchange = (e) => {
      const file = (e.target as HTMLInputElement).files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const sanitized = sanitizeConfig(JSON.parse(reader.result as string));
          if (!sanitized) {
            alert("Invalid config file.");
            return;
          }
          setConfig(sanitized);
          setActivePreset("custom");
        } catch {
          alert("Invalid config file.");
        }
      };
      reader.readAsText(file);
    };
    input.click();
  }

  return (
    <div className="mx-auto max-w-5xl px-6 py-12">
      <div className="mb-8 flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Configure Your World</h1>
          <p className="mt-1 text-zinc-400">
            Start from a preset or dial in every parameter, then generate a downloadable world.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={importConfig}
            className="rounded-lg border border-zinc-700 px-4 py-2 text-sm text-zinc-400 hover:border-zinc-500 hover:text-zinc-200"
          >
            Import
          </button>
          <button
            onClick={exportConfig}
            className="rounded-lg border border-zinc-700 px-4 py-2 text-sm text-zinc-400 hover:border-zinc-500 hover:text-zinc-200"
          >
            Export
          </button>
        </div>
      </div>

      <div className="mb-8">
        <h2 className="mb-3 text-sm font-medium uppercase tracking-wider text-zinc-500">Presets</h2>
        <div className="flex flex-wrap gap-2">
          {PRESETS.map((preset) => (
            <button
              key={preset.id}
              onClick={() => applyPreset(preset.id)}
              className={`rounded-lg border px-4 py-2 text-sm transition-colors ${
                activePreset === preset.id
                  ? "border-emerald-500 bg-emerald-500/10 text-emerald-400"
                  : "border-zinc-700 text-zinc-400 hover:border-zinc-500 hover:text-zinc-200"
              }`}
            >
              {preset.name}
            </button>
          ))}
          {activePreset === "custom" && (
            <span className="rounded-lg border border-zinc-600 bg-zinc-800 px-4 py-2 text-sm text-zinc-300">
              Custom
            </span>
          )}
        </div>
        {activePreset !== "custom" && (
          <p className="mt-2 text-sm text-zinc-500">
            {PRESETS.find((p) => p.id === activePreset)?.description}
          </p>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        {SECTIONS.map((section) => (
          <ConfigPanel
            key={section.key}
            title={section.title}
            fields={section.fields}
            values={config[section.key] as unknown as Record<string, number | boolean>}
            onChange={(key, value) => updateField(section.key, key, value)}
          />
        ))}
      </div>

      <div className="mt-10 flex flex-col items-end gap-3">
        {genStatus && <p className="text-sm text-zinc-400">{genStatus}</p>}
        <button
          onClick={handleGenerate}
          disabled={generating}
          className="rounded-lg bg-emerald-600 px-8 py-3 text-lg font-medium text-white hover:bg-emerald-500 disabled:opacity-50"
        >
          {generating ? "Generating…" : "Generate World"}
        </button>
      </div>
    </div>
  );
}
