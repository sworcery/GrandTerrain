package com.grandterrain.worldgen.cave;

import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.noise.ContinentalNoise;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Underground river channels via Voronoi edge detection. Rivers follow cell
 * edges; water fills the bottom of each carved channel, air above.
 *
 * River depth scales with seaLevel - default sea level 128 puts rivers around
 * y = -80 (144 blocks below sea), and the band moves with seaLevel.
 *
 * Per-column XZ noise is cached in a ThreadLocal so the depth-noise and
 * Voronoi evaluations run once per (x, z) per thread.
 */
public class UndergroundRiverCarver implements CaveContributor {

    private static final double RIVER_RANGE = 6.0;
    private static final double RIVER_WIDTH = 0.12;

    /** River centre-line is this many blocks below sea level (default: 208 below). */
    private static final int RIVER_DEPTH_BELOW_SEA = 208;

    /** Vertical wander of the centre-line (peak-to-peak ~100 blocks). */
    private static final double RIVER_VERTICAL_WANDER = 50.0;

    private final FastNoiseLite edgeNoise;
    private final FastNoiseLite depthNoise;
    private final boolean enabled;
    private final double riverBaseCenter;    // y offset of centre line
    private final int minY;
    private final int maxY;

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
        this.riverBaseCenter = config.seaLevel() - RIVER_DEPTH_BELOW_SEA;

        // Band: centre +/- wander +/- RIVER_RANGE, clamped to not go below bedrock floor
        int proposedMinY = (int) Math.floor(riverBaseCenter - RIVER_VERTICAL_WANDER - RIVER_RANGE);
        int proposedMaxY = (int) Math.ceil(riverBaseCenter + RIVER_VERTICAL_WANDER + RIVER_RANGE);
        this.minY = Math.max(config.worldMinY() + 16, proposedMinY);
        this.maxY = proposedMaxY;

        edgeNoise = new FastNoiseLite((int) (seed ^ 0x2190E200L));
        edgeNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        edgeNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
        edgeNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2Sub);
        edgeNoise.SetFrequency(1.0f / 300.0f);

        depthNoise = new FastNoiseLite((int) (seed ^ 0x2190E201L));
        depthNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        depthNoise.SetFrequency(1.0f / 500.0f);
    }

    @Override public int minY() { return minY; }
    @Override public int maxY() { return maxY; }

    private ColumnCache ensureColumn(double x, double z) {
        long kx = Double.doubleToLongBits(x == 0.0 ? 0.0 : x);
        long kz = Double.doubleToLongBits(z == 0.0 ? 0.0 : z);
        ColumnCache c = columnCache.get();
        if (c.valid && c.keyX == kx && c.keyZ == kz) return c;

        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);
        c.riverBaseY = riverBaseCenter + depthNoise.GetNoise(fx, fz) * RIVER_VERTICAL_WANDER;
        c.edgeDist = Math.abs(edgeNoise.GetNoise(fx, fz));
        c.keyX = kx;
        c.keyZ = kz;
        c.valid = true;
        return c;
    }

    @Override
    public CarveResult sample(double x, double y, double z) {
        if (!enabled) return CarveResult.SOLID;

        ColumnCache c = ensureColumn(x, z);
        if (c.edgeDist > RIVER_WIDTH) return CarveResult.SOLID;

        double verticalDist = Math.abs(y - c.riverBaseY);
        if (verticalDist > RIVER_RANGE) return CarveResult.SOLID;

        double proximity = 1.0 - (c.edgeDist / RIVER_WIDTH);
        double verticalFade = 1.0 - (verticalDist / RIVER_RANGE);
        if (proximity * verticalFade - 0.3 <= 0) return CarveResult.SOLID;

        return y <= c.riverBaseY + 1 ? CarveResult.CARVE_WATER : CarveResult.CARVE_AIR;
    }
}
