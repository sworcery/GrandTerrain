package com.grandterrain.worldgen.cave;

import com.grandterrain.config.GrandterrainConfig;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Generates underground river channels using Voronoi edge detection.
 * Rivers follow the edges between Voronoi cells, creating natural branching patterns.
 * Filled with water during block placement.
 */
public class UndergroundRiverCarver {

    private final FastNoiseLite edgeNoise;
    private final FastNoiseLite depthNoise;
    private final boolean enabled;

    public UndergroundRiverCarver(long seed, GrandterrainConfig config) {
        this.enabled = config.enableUndergroundRivers;

        edgeNoise = new FastNoiseLite((int) (seed + 25000));
        edgeNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        edgeNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq);
        edgeNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2Sub);
        edgeNoise.SetFrequency(1.0f / 300.0f);

        depthNoise = new FastNoiseLite((int) (seed + 25100));
        depthNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        depthNoise.SetFrequency(1.0f / 500.0f);
    }

    /**
     * Returns the carve value for an underground river at this position.
     * Positive = carve (air or water), negative = solid.
     */
    public double sampleCarve(double x, double y, double z) {
        if (!enabled) return -1.0;

        float fx = (float) x;
        float fz = (float) z;

        double riverBaseY = -80.0 + depthNoise.GetNoise(fx, fz) * 50.0;
        double riverRange = 6.0;

        double verticalDist = Math.abs(y - riverBaseY);
        if (verticalDist > riverRange) return -1.0;

        double edge = edgeNoise.GetNoise(fx, fz);
        double riverWidth = 0.12;
        double edgeDist = Math.abs(edge);

        if (edgeDist > riverWidth) return -1.0;

        double proximity = 1.0 - (edgeDist / riverWidth);
        double verticalFade = 1.0 - (verticalDist / riverRange);

        return proximity * verticalFade - 0.3;
    }

    /**
     * Returns whether this position should be filled with water (vs air).
     * Only meaningful when sampleCarve() > 0.
     */
    public boolean sampleIsWater(double x, double y, double z) {
        if (!enabled) return false;

        float fx = (float) x;
        float fz = (float) z;

        double riverBaseY = -80.0 + depthNoise.GetNoise(fx, fz) * 50.0;
        return y <= riverBaseY + 1;
    }
}
