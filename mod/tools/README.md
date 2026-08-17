# Worldgen evaluation tools

The mod's `worldgen` noise/cave/river packages are Minecraft-free, so terrain
can be evaluated headlessly — no server or client needed. These tools caught
the origin-seam, hollow-world, and inverted-Voronoi bugs that three rounds of
code review missed; run them after any worldgen change.

Compile (repo root; needs the JDK the mod targets):

```bash
# ConfigSnapshot carries a DFU codec, so DataFixerUpper (+gson, fastutil) must be
# on the classpath — grab them from ~/.gradle/caches after a mod build:
CP=$(find ~/.gradle/caches -name 'datafixerupper-9*.jar' -o -name 'gson-2.9*.jar' -o -name 'fastutil-8*.jar' | grep -v sources | tr '\n' ':')
javac -cp "$CP" -sourcepath mod/src/main/java -d /tmp/gt-tools mod/tools/*.java
# then run with:  java -cp "$CP/tmp/gt-tools" <Tool>
```

| Tool | Run | What it shows |
|---|---|---|
| `TerrainPreview` | `java -cp /tmp/gt-tools TerrainPreview <seed> <cx> <cz> <sizeBlocks> <step> <outDir>` | elevation/biome/climate maps + full-depth cross-section + height/biome stats, using the production noise code |
| `CaveProbe` | `java -cp /tmp/gt-tools CaveProbe <seed>` | per-contributor carve % by Y band (catches over/under-carving) |
| `RangeProbe` | `java -cp /tmp/gt-tools RangeProbe` | empirical output ranges of every FNL configuration — thresholds must be calibrated against these, not assumptions |
| `SettingsRoundTrip` | `java -cp "$CP/tmp/gt-tools" SettingsRoundTrip` | proves world settings encode/decode exactly and survive a different local config (the pregen-seam regression gate; exits non-zero on failure) |
| `region_render.py` | `python3 mod/tools/region_render.py <worldDir> <outDir> [sliceY]` | ground truth from real generated chunks: surface/height/biome maps + underground slice (needs `nbtlib`, `Pillow`) |

These sources live outside the Gradle source sets and never ship in the jar.
