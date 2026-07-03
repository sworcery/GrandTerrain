import type { WorldConfig } from "./config-schema";

const WORKER_URL =
  process.env.NEXT_PUBLIC_WORKER_URL ?? "http://localhost:8080";

export interface JobResponse {
  jobId: string;
  status: "pending" | "generating" | "ready" | "failed";
  createdAt: string;
}

export async function generateWorld(config: WorldConfig): Promise<JobResponse> {
  const res = await fetch(`${WORKER_URL}/api/generate`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ config }),
  });
  if (!res.ok) throw new Error(`Generation request failed: ${res.status}`);
  return res.json();
}

export async function getJobStatus(jobId: string): Promise<JobResponse> {
  const res = await fetch(`${WORKER_URL}/api/jobs/${jobId}`);
  if (!res.ok) throw new Error(`Job lookup failed: ${res.status}`);
  return res.json();
}

export function downloadUrl(jobId: string): string {
  return `${WORKER_URL}/api/jobs/${jobId}/download`;
}
