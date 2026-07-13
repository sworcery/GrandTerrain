package com.grandterrain.worldgen.cave;

import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.noise.ContinentalNoise;
import com.grandterrain.worldgen.noise.FastNoiseLite;

public class CavernGenerator implements CaveContributor {

    /**
     * FNL Cellular/Distance (Euclidean) measured output: [-1, +0.05] with
     * cell CENTERS at -1 (see mod/tools/RangeProbe). d01 = value + 1 gives a
     * 0-at-center distance. Caverns spawn where d01 < CAVERN_THRESHOLD —
     * one pocket around each Voronoi cell center (cells ~500 blocks).
     * The old code compared the raw value against 0.08 as if the range were
     * [0, 1], which was true for 100% of terrain and hollowed out the world.
     */
    private static final double CAVERN_THRESHOLD = 0.25;

    private final FastNoiseLite cellNoise;
    private final FastNoiseLite floorNoise;
    private final boolean enabled;
    private final int seaLevel;
    private final int bedrockFloor;
    private final int cavernCenterY;

    private static final class ColumnCache {
        long keyX = 1L;
        long keyZ = 1L;
        boolean valid;
        double cellDist;
        double proximity;
        double cavernRadius;
        double centerY;
        double verticalRadius;
    }
    private final ThreadLocal<ColumnCache> columnCache = ThreadLocal.withInitial(ColumnCache::new);

    public CavernGenerator(long seed, ConfigSnapshot config) {
        this.enabled = config.enableMegaCaverns();
        this.seaLevel = config.seaLevel();
        this.bedrockFloor = config.worldMinY() + 30;
        this.cavernCenterY = config.cavernCenterY();

        cellNoise = new FastNoiseLite((int) (seed ^ 0xCA4EC0DEL));
        cellNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        cellNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
        cellNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance);
        cellNoise.SetFrequency(1.0f / 500.0f);

        floorNoise = new FastNoiseLite((int) (seed ^ 0xC4F100E7L));
        floorNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        floorNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        floorNoise.SetFractalOctaves(3);
        floorNoise.SetFrequency(1.0f / 30.0f);
    }

    // Caverns live at cavernCenterY ± (40 wander + up to 36 vertical radius);
    // bound the contributor to that band so the pipeline early-out works.
    @Override public int minY() { return Math.max(bedrockFloor, cavernCenterY - 80); }
    @Override public int maxY() { return Math.min(seaLevel - 40, cavernCenterY + 80); }

    private ColumnCache ensureColumn(double x, double z) {
        long kx = Double.doubleToLongBits(x == 0.0 ? 0.0 : x);
        long kz = Double.doubleToLongBits(z == 0.0 ? 0.0 : z);
        ColumnCache c = columnCache.get();
        if (c.valid && c.keyX == kx && c.keyZ == kz) return c;

        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);

        c.cellDist = cellNoise.GetNoise(fx, fz) + 1.0; // 0 at cell center (measured)
        if (c.cellDist <= CAVERN_THRESHOLD) {
            c.proximity = 1.0 - (c.cellDist / CAVERN_THRESHOLD); // [0,1], 1 at center
            c.cavernRadius = 25.0 + c.proximity * 35.0;
            c.centerY = cavernCenterY + floorNoise.GetNoise(fx * 0.5f, fz * 0.5f) * 40.0;
            c.verticalRadius = c.cavernRadius * 0.6;
        }
        c.keyX = kx;
        c.keyZ = kz;
        c.valid = true;
        return c;
    }

    @Override
    public CarveResult sample(double x, double y, double z) {
        if (!enabled) return CarveResult.SOLID;

        ColumnCache c = ensureColumn(x, z);

        if (c.cellDist > CAVERN_THRESHOLD) return CarveResult.SOLID;

        double verticalDist = Math.abs(y - c.centerY);
        if (verticalDist > c.verticalRadius) return CarveResult.SOLID;

        double verticalFade = 1.0 - (verticalDist / c.verticalRadius);

        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);
        double floorOffset = floorNoise.GetNoise(fx, (float) y, fz) * 8.0;
        double floorY = c.centerY - c.verticalRadius + 5.0 + floorOffset;

        if (y < floorY) return CarveResult.SOLID;

        return (c.proximity * verticalFade - 0.2) > 0
                ? CarveResult.CARVE_AIR
                : CarveResult.SOLID;
    }
}
