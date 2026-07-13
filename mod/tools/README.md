# Worldgen evaluation tools

The mod's `worldgen` noise/cave/river packages are Minecraft-free, so terrain
can be evaluated headlessly — no server or client needed. These tools caught
the origin-seam, hollow-world, and inverted-Voronoi bugs that three rounds of
code review missed; run them after any worldgen change.

Compile (repo root; needs the JDK the mod targets):

```bash
javac -sourcepath mod/src/main/java -d /tmp/gt-tools mod/tools/*.java
```

| Tool | Run | What it shows |
|---|---|---|
| `TerrainPreview` | `java -cp /tmp/gt-tools TerrainPreview <seed> <cx> <cz> <sizeBlocks> <step> <outDir>` | elevation/biome/climate maps + full-depth cross-section + height/biome stats, using the production noise code |
| `CaveProbe` | `java -cp /tmp/gt-tools CaveProbe <seed>` | per-contributor carve % by Y band (catches over/under-carving) |
| `RangeProbe` | `java -cp /tmp/gt-tools RangeProbe` | empirical output ranges of every FNL configuration — thresholds must be calibrated against these, not assumptions |
| `region_render.py` | `python3 mod/tools/region_render.py <worldDir> <outDir> [sliceY]` | ground truth from real generated chunks: surface/height/biome maps + underground slice (needs `nbtlib`, `Pillow`) |

These sources live outside the Gradle source sets and never ship in the jar.
