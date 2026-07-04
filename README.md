# GrandTerrain

A Fabric mod for **massive, realistic Minecraft world generation** — towering mountains, deep multi-layer cave systems, underground rivers, and 16 custom biomes across a 1024-block-tall world — plus a web configurator to dial in the terrain and generate a downloadable world.

Built to pair with [Voxy](https://modrinth.com/mod/voxy) for extreme render distances, so the scale of the terrain is actually visible.

> **Status: early / experimental.** The mod generates complete worlds in a dedicated server, but this is a young project targeting a bleeding-edge Minecraft version. Expect rough edges. Contributions and bug reports welcome.

## Features

- Continental-scale terrain with ridged mountains, macro erosion, and Voronoi valleys
- Layered cave system: cheese caves, spaghetti caves, mega caverns, and underground rivers
- 16 custom biomes selected by a temperature/humidity/altitude climate model
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

## Running the worker (generation backend)

```bash
cd worker
docker compose up --build
```

On first boot the container downloads everything it needs (Fabric server
launcher, Fabric API, [Chunky](https://modrinth.com/plugin/chunky), and the
GrandTerrain mod jar) into the mounted `data/` volume. Each generation job
boots a headless Fabric server, pre-generates a square region around spawn
with Chunky, and packages the world into a downloadable zip.

Key environment knobs (see `worker/.env.example` for the full list):

| Variable | Default | Meaning |
|---|---|---|
| `PREGEN_RADIUS` | `2000` | Pre-generation radius in blocks (2000 → a 4000×4000 area); `0` disables |
| `PREGEN_TIMEOUT` | `21600` | Hard cap in seconds; partial pre-gen still ships |
| `MAX_CONCURRENT_JOBS` | `1` | Generation jobs allowed at once |
| `JOB_RETENTION_HOURS` | `24` | Age after which job dirs and world zips are deleted |

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
