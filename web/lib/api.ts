import type { WorldConfig } from "./config-schema";

// Same-origin by default: /worker/* is proxied to the worker by
// app/worker/[...path]/route.ts using the server-side WORKER_URL env var.
// NEXT_PUBLIC_WORKER_URL remains an optional build-time override for calling
// the worker directly (cross-origin; requires the worker's CORS to allow it).
// `||` not `??`: the Dockerfile's optional ARG inlines an empty string when unset.
const WORKER_URL = process.env.NEXT_PUBLIC_WORKER_URL || "/worker";

export interface JobResponse {
  jobId: string;
  status: "pending" | "generating" | "ready" | "failed";
  createdAt: string;
  /** Present on failed jobs when the worker can explain why. */
  error?: string;
}

/**
 * Surface the API's own error message when it has one — the worker returns a
 * helpful `detail` on 429 ("server busy"), which is the most common non-happy
 * path with a single generation slot.
 */
async function errorFrom(res: Response, fallback: string): Promise<Error> {
  let detail: unknown;
  try {
    detail = (await res.json())?.detail;
  } catch {
    // non-JSON body — fall through to the generic message
  }
  // FastAPI 422 validation errors put an array in `detail`; only surface strings.
  const message = typeof detail === "string" ? detail : "";
  return new Error(message || `${fallback} (HTTP ${res.status})`);
}

export async function generateWorld(config: WorldConfig): Promise<JobResponse> {
  const res = await fetch(`${WORKER_URL}/api/generate`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ config }),
  });
  if (!res.ok) throw await errorFrom(res, "Generation request failed");
  return res.json();
}

/** Returns null when the job definitively no longer exists (404) — callers
 * must treat that as terminal, not as a transient network blip. */
export async function getJobStatus(jobId: string): Promise<JobResponse | null> {
  const res = await fetch(`${WORKER_URL}/api/jobs/${jobId}`);
  if (res.status === 404) return null;
  if (!res.ok) throw new Error(`Job lookup failed: ${res.status}`);
  return res.json();
}

export function downloadUrl(jobId: string): string {
  return `${WORKER_URL}/api/jobs/${jobId}/download`;
}
