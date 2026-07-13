"""Render a real generated world's region files: surface map, heightmap,
biome map, and an underground air-slice. Ground truth for what production
chunks actually contain (vs. the TerrainPreview projections)."""
import io
import json
import struct
import sys
import zlib
from pathlib import Path

import nbtlib
from PIL import Image

if len(sys.argv) < 3:
    sys.exit("usage: region_render.py <worldDir> <outDir> [sliceY=-100]")
WORLD = Path(sys.argv[1])
OUT = Path(sys.argv[2])
SLICE_Y = int(sys.argv[3]) if len(sys.argv) > 3 else -100
OUT.mkdir(parents=True, exist_ok=True)

REGION_DIR = WORLD / "dimensions/minecraft/overworld/region"

BLOCK_COLORS = {
    "minecraft:water": (47, 109, 179), "minecraft:lava": (207, 92, 20),
    "minecraft:stone": (127, 127, 127), "minecraft:deepslate": (80, 78, 86),
    "minecraft:grass_block": (98, 160, 62), "minecraft:dirt": (134, 96, 67),
    "minecraft:sand": (219, 207, 163), "minecraft:gravel": (136, 126, 126),
    "minecraft:snow": (240, 244, 250), "minecraft:snow_block": (240, 244, 250),
    "minecraft:water_frozen": (160, 188, 220), "minecraft:ice": (160, 188, 220),
    "minecraft:clay": (159, 164, 177), "minecraft:coarse_dirt": (110, 79, 55),
    "minecraft:podzol": (90, 63, 24), "minecraft:mossy_cobblestone": (110, 118, 94),
    "minecraft:bedrock": (20, 20, 20), "minecraft:air": (0, 0, 0),
}
BIOME_COLORS = {
    "grandterrain:deep_ocean": (11, 61, 99), "grandterrain:coastal_cliffs": (138, 141, 145),
    "grandterrain:lowland_plains": (127, 179, 91), "grandterrain:temperate_forest": (46, 125, 50),
    "grandterrain:alpine_meadow": (168, 208, 141), "grandterrain:mountain_pine_forest": (27, 94, 32),
    "grandterrain:rocky_highlands": (158, 158, 158), "grandterrain:snow_peaks": (245, 247, 250),
    "grandterrain:deep_valley": (85, 139, 47), "grandterrain:volcanic_region": (109, 63, 47),
    "grandterrain:desert": (230, 199, 128), "grandterrain:savanna": (196, 169, 75),
    "grandterrain:swamp": (78, 107, 67), "grandterrain:tundra": (207, 227, 234),
    "grandterrain:birch_forest": (217, 232, 184), "grandterrain:dark_forest": (26, 61, 28),
}


def iter_chunks(path):
    data = path.read_bytes()
    for i in range(1024):
        off = struct.unpack(">I", b"\0" + data[i * 4:i * 4 + 3])[0]
        if off == 0:
            continue
        pos = off * 4096
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        comp = data[pos + 4]
        payload = data[pos + 5:pos + 4 + length]
        if comp != 2:
            continue
        try:
            yield nbtlib.File.parse(io.BytesIO(zlib.decompress(payload)))
        except Exception:
            continue


def section_blocks(sec):
    """Return (palette names, 4096 indices or None-if-single)."""
    bs = sec.get("block_states")
    if bs is None:
        return None, None
    palette = [str(e["Name"]) for e in bs.get("palette", [])]
    raw = bs.get("data")
    if raw is None:
        return palette, None
    bits = max(4, (len(palette) - 1).bit_length())
    per = 64 // bits
    mask = (1 << bits) - 1
    idx = []
    for word in raw:
        w = int(word) & 0xFFFFFFFFFFFFFFFF
        for _ in range(per):
            idx.append(w & mask)
            w >>= bits
            if len(idx) >= 4096:
                break
    return palette, idx


def section_biomes(sec):
    bio = sec.get("biomes")
    if bio is None:
        return None, None
    palette = [str(e) for e in bio.get("palette", [])]
    raw = bio.get("data")
    if raw is None:
        return palette, None
    bits = max(1, (len(palette) - 1).bit_length())
    per = 64 // bits
    mask = (1 << bits) - 1
    idx = []
    for word in raw:
        w = int(word) & 0xFFFFFFFFFFFFFFFF
        for _ in range(per):
            idx.append(w & mask)
            w >>= bits
            if len(idx) >= 64:
                break
    return palette, idx


chunks = {}
for mca in sorted(REGION_DIR.glob("*.mca")):
    for nbt in iter_chunks(mca):
        if str(nbt.get("Status", "")) != "minecraft:full":
            continue
        cx, cz = int(nbt["xPos"]), int(nbt["zPos"])
        chunks[(cx, cz)] = nbt

if not chunks:
    sys.exit("no full chunks found")

xs = [c[0] for c in chunks]; zs = [c[1] for c in chunks]
x0, x1 = min(xs), max(xs); z0, z1 = min(zs), max(zs)
W, H = (x1 - x0 + 1) * 16, (z1 - z0 + 1) * 16
print(f"full chunks: {len(chunks)}  area: chunks x[{x0}..{x1}] z[{z0}..{z1}] -> {W}x{H} px")

surface = Image.new("RGB", (W, H), (10, 10, 14))
heightimg = Image.new("RGB", (W, H), (0, 0, 0))
biomeimg = Image.new("RGB", (W, H), (10, 10, 14))
sliceimg = Image.new("RGB", (W, H), (10, 10, 14))
sp, hp, bp, lp = surface.load(), heightimg.load(), biomeimg.load(), sliceimg.load()

heights = {}
for (cx, cz), nbt in chunks.items():
    ox, oz = (cx - x0) * 16, (cz - z0) * 16
    secs = {int(s["Y"]): s for s in nbt.get("sections", [])}
    order = sorted(secs.keys(), reverse=True)

    # decode sections lazily per chunk
    decoded = {}
    def blocks_at(sy):
        if sy not in decoded:
            s = secs.get(sy)
            decoded[sy] = section_blocks(s) if s is not None else (None, None)
        return decoded[sy]

    for lz in range(16):
        for lx in range(16):
            # surface scan top-down
            placed = False
            for sy in order:
                pal, idx = blocks_at(sy)
                if pal is None or pal == ["minecraft:air"]:
                    continue
                for ly in range(15, -1, -1):
                    if idx is None:
                        name = pal[0]
                    else:
                        name = pal[idx[ly * 256 + lz * 16 + lx]]
                    if name != "minecraft:air" and name != "minecraft:cave_air":
                        y = sy * 16 + ly
                        c = BLOCK_COLORS.get(name, (200, 60, 200))
                        sp[ox + lx, oz + lz] = c
                        g = max(0, min(255, int((y + 256) / 1024 * 255)))
                        hp[ox + lx, oz + lz] = (g, g, g)
                        heights[(ox + lx, oz + lz)] = y
                        placed = True
                        break
                if placed:
                    break

    # biome at surface-ish (sample stored biome at y=seaLevel+16 -> section 9, or top non-empty)
    for lz in range(16):
        for lx in range(16):
            y = heights.get((ox + lx, oz + lz), 128)
            sy = min(max(y // 16, -16), 47)
            s = secs.get(sy)
            if s is None:
                continue
            bpal, bidx = section_biomes(s)
            if bpal is None:
                continue
            if bidx is None:
                name = bpal[0]
            else:
                qy = (y % 16) // 4
                name = bpal[bidx[qy * 16 + (lz // 4) * 4 + (lx // 4)]]
            bp[ox + lx, oz + lz] = BIOME_COLORS.get(name, (200, 60, 200))

    # underground slice
    sy = SLICE_Y // 16
    pal, idx = blocks_at(sy)
    if pal is not None:
        ly = SLICE_Y - sy * 16
        for lz in range(16):
            for lx in range(16):
                name = pal[0] if idx is None else pal[idx[ly * 256 + lz * 16 + lx]]
                if name in ("minecraft:air", "minecraft:cave_air"):
                    c = (0, 0, 0)
                elif name == "minecraft:water":
                    c = (47, 109, 179)
                else:
                    c = (120, 116, 110)
                lp[ox + lx, oz + lz] = c

surface.save(OUT / "real_surface.png")
heightimg.save(OUT / "real_height.png")
biomeimg.save(OUT / "real_biomes.png")
sliceimg.save(OUT / f"real_slice_y{SLICE_Y}.png")
hs = sorted(heights.values())
print(f"surface height: min={hs[0]} p50={hs[len(hs)//2]} max={hs[-1]}")
print("wrote", OUT)
