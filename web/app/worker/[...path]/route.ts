import type { NextRequest } from "next/server";

// Same-origin proxy for the generation worker. The browser talks to
// /worker/... on this app's origin, and this handler forwards to WORKER_URL
// (a server-side env var read at request time — not baked into the build).
// This avoids mixed-content blocking behind an HTTPS reverse proxy and lets
// docker-compose point at the container-internal hostname (http://worker:8080).
//
// This is a Route Handler rather than a next.config `rewrites()` because with
// `output: "standalone"` the resolved config (including rewrite destinations)
// is serialised into server.js at build time, so an env-driven destination
// would go stale exactly like NEXT_PUBLIC_WORKER_URL did.

export const dynamic = "force-dynamic";

function workerUrl(): string {
  return (process.env.WORKER_URL ?? "http://localhost:8080").replace(/\/+$/, "");
}

// Hop-by-hop / transport headers that must not be forwarded verbatim.
const STRIP_RESPONSE_HEADERS = new Set([
  "connection",
  "keep-alive",
  "transfer-encoding",
  "content-encoding",
]);
// content-length is only wrong when Node's fetch transparently decompressed
// the body — i.e. when upstream sent content-encoding. The worker never
// compresses, so keeping it lets the browser show real download progress on
// a multi-hundred-MB world instead of an unknown-size transfer.
function keepsContentLength(upstream: Response): boolean {
  return !upstream.headers.has("content-encoding");
}
const STRIP_REQUEST_HEADERS = new Set(["host", "connection", "content-length"]);

async function proxy(req: NextRequest, path: string[]): Promise<Response> {
  const target = `${workerUrl()}/${path.map(encodeURIComponent).join("/")}${req.nextUrl.search}`;

  const headers = new Headers();
  req.headers.forEach((value, key) => {
    if (!STRIP_REQUEST_HEADERS.has(key.toLowerCase())) headers.set(key, value);
  });

  // Request bodies are small JSON configs — buffer them (avoids undici's
  // stream `duplex` requirement). Next's patched fetch still transmits the
  // body with chunked transfer encoding, which uvicorn decodes fine; verified
  // end-to-end against the standalone build. Response bodies (large zips)
  // stream straight through.
  const hasBody = req.method !== "GET" && req.method !== "HEAD";
  const body = hasBody ? Buffer.from(await req.arrayBuffer()) : undefined;
  let upstream: Response;
  try {
    upstream = await fetch(target, {
      method: req.method,
      headers,
      body,
      redirect: "manual",
      cache: "no-store",
    });
  } catch {
    return Response.json({ detail: "Generation worker is unreachable." }, { status: 502 });
  }

  const responseHeaders = new Headers();
  upstream.headers.forEach((value, key) => {
    const k = key.toLowerCase();
    if (STRIP_RESPONSE_HEADERS.has(k)) return;
    if (k === "content-length" && !keepsContentLength(upstream)) return;
    responseHeaders.set(key, value);
  });

  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}

type Ctx = { params: Promise<{ path: string[] }> };

export async function GET(req: NextRequest, ctx: Ctx) {
  return proxy(req, (await ctx.params).path);
}

export async function HEAD(req: NextRequest, ctx: Ctx) {
  return proxy(req, (await ctx.params).path);
}

export async function POST(req: NextRequest, ctx: Ctx) {
  return proxy(req, (await ctx.params).path);
}
