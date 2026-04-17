package com.grandterrain.worldgen.cave;

import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.noise.ContinentalNoise;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Generates underground river channels using Voronoi edge detection.
 * Rivers follow the edges between Voronoi cells, creating natural branching patterns.
 *
 * Per-column XZ noise values are cached in a ThreadLocal so sampleCarve() and
 * sampleIsWater() can share one noise evaluation per (x, z) pair.
 */
public class UndergroundRiverCarver {

    private static final double RIVER_RANGE = 6.0;
    private static final double RIVER_WIDTH = 0.12;

    private final FastNoiseLite edgeNoise;
    private final FastNoiseLite depthNoise;
    private final boolean enabled;

    /** Per-thread cache of the last-sampled (x, z) column's riverBaseY and edgeDist. */
    private static final class ColumnCache {
        long keyX = 1L;
        long keyZ = 1L;
        boolean valid;
        double riverBaseY;
        double edgeDist;
    }
    private final ThreadLocal<ColumnCache> columnCache = ThreadLocal.withInitial(ColumnCache::new);

    public UndergroundRiverCarver(long seed, ConfigSnapshot config) {
        this.enabled = config.enableUndergroundRivers();

        // Linear Euclidean so Distance2Sub returns its documented [-1, 1]-ish range.
        edgeNoise = new FastNoiseLite((int) (seed ^ 0x2190E200L));
        edgeNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        edgeNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
        edgeNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2Sub);
        edgeNoise.SetFrequency(1.0f / 300.0f);

        depthNoise = new FastNoiseLite((int) (seed ^ 0x2190E201L));
        depthNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        depthNoise.SetFrequency(1.0f / 500.0f);
    }

    private ColumnCache ensureColumn(double x, double z) {
        long kx = Double.doubleToLongBits(x == 0.0 ? 0.0 : x);
        long kz = Double.doubleToLongBits(z == 0.0 ? 0.0 : z);
        ColumnCache c = columnCache.get();
        if (c.valid && c.keyX == kx && c.keyZ == kz) return c;

        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);
        c.riverBaseY = -80.0 + depthNoise.GetNoise(fx, fz) * 50.0;
        c.edgeDist = Math.abs(edgeNoise.GetNoise(fx, fz));
        c.keyX = kx;
        c.keyZ = kz;
        c.valid = true;
        return c;
    }

    /**
     * Returns the carve value for an underground river.
     * Positive = carve, negative = solid.
     */
    public double sampleCarve(double x, double y, double z) {
        if (!enabled) return -1.0;

        ColumnCache c = ensureColumn(x, z);
        if (c.edgeDist > RIVER_WIDTH) return -1.0;

        double verticalDist = Math.abs(y - c.riverBaseY);
        if (verticalDist > RIVER_RANGE) return -1.0;

        double proximity = 1.0 - (c.edgeDist / RIVER_WIDTH);
        double verticalFade = 1.0 - (verticalDist / RIVER_RANGE);
        return proximity * verticalFade - 0.3;
    }

    /**
     * Returns whether this position should be filled with water (vs air).
     * Only meaningful when sampleCarve() > 0. Uses the cached column value.
     */
    public boolean sampleIsWater(double x, double y, double z) {
        if (!enabled) return false;
        ColumnCache c = ensureColumn(x, z);
        return y <= c.riverBaseY + 1;
    }
}
