<p align="center">
  <img src="docs/banner.png" alt="GrandTerrain — 16-bit mountain vista with a glowing cavern beneath" width="100%">
</p>

# GrandTerrain

A Fabric mod for **massive, realistic Minecraft world generation**: towering mountains, deep multi-layer cave systems, underground rivers, and 17 custom biomes across a 1024-block-tall world. The repo also includes a web configurator for tuning the terrain and generating a downloadable world.

Built to pair with [Voxy](https://modrinth.com/mod/voxy) for extreme render distances, so the scale of the terrain is actually visible.

> **Status: early / experimental.** The mod generates complete worlds in a dedicated server, but this is a young project targeting a bleeding-edge Minecraft version. Expect rough edges. Contributions and bug reports welcome.

## Features

- Continental-scale terrain with ridged mountains, macro erosion, and Voronoi valleys
- Layered cave system: cheese caves, spaghetti caves, mega caverns, and underground rivers
- 16 surface biomes selected by a temperature/humidity/altitude climate model, plus a caverns biome for everything below the terrain surface, so caves spawn normal cave mobs
- Config-driven structures (castles, ruins, dungeons, watchtowers) with biome filtering
- Fully configurable via an in-game [Cloth Config](https://modrinth.com/mod/cloth-config) screen or a `grandterrain.properties` file
- Web configurator for tuning every parameter and exporting/generating worlds

## Requirements

| | Version |
|---|---|
| Minecraft | 26.1.2 |
| Loader | Fabric 0.18.6+ |
| Fabric API | 0.145.4+26.1.2 |
| Java | 25 |
| Voxy (optional, recommended) | 0.2.16-beta+ |

Voxy is client-side and optional, but it's the reason the terrain scale is worth it — install it to render the world at far distances.

## Repository layout

```
mod/     — the GrandTerrain Fabric mod (Java)
web/     — Next.js configurator: tune parameters, generate & download a world
worker/  — backend that runs a headless Fabric server to generate a world from a config
```

## Deploying on Unraid (or any Docker host)

The full stack is two containers: the web configurator (port 3000) and the
generation worker (port 8080). The web UI proxies all worker calls same-origin
under `/worker`, so browsers only ever need to reach the web port.

```bash
git clone https://github.com/sworcery/GrandTerrain.git
cd GrandTerrain
cp .env.example .env      # optional: edit ports / memory / radius
docker compose up --build -d
```

Docker Compose auto-loads `./.env`. Every variable is optional; see
[`.env.example`](.env.example) for the full list. You do **not** need to set
`NEXT_PUBLIC_WORKER_URL` — it is only for making the browser talk to the worker
directly (e.g. via a LAN IP), and because it is baked into the web bundle at
build time, changing it requires `docker compose up --build`, not a restart.

**First boot** downloads four jars into the data volume: the Fabric server
launcher, Fabric API, [Chunky](https://modrinth.com/plugin/chunky), and the
GrandTerrain mod jar from the GitHub release. This can take a few minutes.
Check progress with:

```bash
docker compose logs worker | grep -i fetched
curl http://<host>:8080/health
```

**Resource requirements**

- **RAM: ~8 GB free for the generation JVM** (`SERVER_MEMORY=6G` heap plus JVM
  overhead). Do **not** set a container memory limit below that — the kernel
  OOM-killer kills the JVM silently and you get a truncated or failed world.
- **Disk: ~5 GB free per job** at defaults (the world tree and its zip coexist
  until cleanup). The worker refuses to start a job below `MIN_FREE_DISK_GB`.
- **CPU:** pegged for the whole job — roughly 35 min to an hour at the default
  2000-block radius depending on hardware.

**One process only.** The worker's job slot is in-memory and assumes a single
uvicorn process. Do not run multiple replicas or `--workers N`; leave
`MAX_CONCURRENT_JOBS=1`.

**Reverse proxy.** Because worker calls are proxied same-origin at `/worker`,
you can put the web port behind HTTPS (SWAG, Nginx Proxy Manager, Cloudflare
Tunnel) and it just works — only the web port needs exposing. Port 8080 can
stay LAN-only or unpublished.

**Sanity checks**

```bash
curl http://<host>:8080/health
docker compose logs worker
```

**Data.** The worker stores jars, job dirs, and finished zips in
`./worker/data` (the `./worker/data:/app/data` volume in `docker-compose.yml`
— point it at `/mnt/user/appdata/grandterrain` on Unraid if you prefer). To
force a re-download of the mod jar (e.g. after a new release), delete
`grandterrain.jar` from that directory and restart the worker.

## Running the worker alone

```bash
cd worker
docker compose up --build
```

On first boot the container downloads everything it needs (Fabric server
launcher, Fabric API, [Chunky](https://modrinth.com/plugin/chunky), and the
GrandTerrain mod jar) into the mounted `data/` volume. Each generation job
boots a headless Fabric server, pre-generates a square region around spawn
with Chunky, and packages the world into a downloadable zip.

**First boot.** The jar downloads happen before the API starts, so the port
is silent for a minute or two the first time (each download retries three
times with backoff). If a download ultimately fails the API still comes up,
but `POST /api/generate` answers `503 Worker not provisioned: <jar> missing`
until the jar exists — check `docker compose logs worker`, fix the network
or override the `*_JAR_URL`, and restart. Nothing is re-downloaded once the
jar is in `data/`.

**Health.** `GET /health` returns `{"status": "ok"|"degraded", "jars": {...},
"active_jobs": n}`; `degraded` means the mod or server jar is missing. The
compose file wires this into a Docker healthcheck (600s start period to
cover first-boot downloads). `GET /` is a small landing JSON pointing at
`/docs` and `/health`. Failed jobs carry a short `error` string in
`GET /api/jobs/{id}` (e.g. out-of-memory, missing jar, packaging failure).

Key environment knobs (see `.env.example` / `worker/.env.example` for the full list):

| Variable | Default | Meaning |
|---|---|---|
| `SERVER_MEMORY` | `6G` | Max heap for the generation server — 1024-tall chunks are memory-hungry; too low OOMs mid-pre-gen |
| `PREGEN_RADIUS` | `2000` | Pre-generation radius in blocks (2000 → a 4000×4000 area); `0` disables |
| `PREGEN_TIMEOUT` | `21600` | Hard cap in seconds; partial pre-gen still ships |
| `MAX_CONCURRENT_JOBS` | `1` | Generation jobs allowed at once (keep at 1 — single-process job slot) |
| `JOB_RETENTION_HOURS` | `24` | Age after which job dirs and world zips are deleted (in-flight jobs are never swept) |
| `MIN_FREE_DISK_GB` | `5` | New jobs are refused with `503 Insufficient disk` below this much free space on the data volume |
| `WORKER_PORT` | `8080` | Host port for the worker API |
| `WEB_PORT` | `3000` | Host port for the web configurator (root stack only) |

Rough sizing from a measured run (~17 chunks/sec on modest hardware): the
default 2000-block radius is ~63k chunks — about an hour and a ~450 MB zip.
Faster hardware scales roughly linearly.

## Building the mod

```bash
./gradlew :mod:build
# artifact: mod/build/libs/grandterrain-<version>.jar
```

The build provisions JDK 25 automatically via the foojay toolchain resolver; a JDK 21+ is required to run the Gradle daemon itself.

## Running the web configurator (dev)

```bash
cd web
npm ci
npm run dev
```

## License

[MIT](LICENSE) © sworcery
