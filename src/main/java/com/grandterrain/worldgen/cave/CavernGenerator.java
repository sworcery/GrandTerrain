package com.grandterrain.worldgen.cave;

import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.noise.ContinentalNoise;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Generates rare mega-caverns using Voronoi cellular noise.
 * At cell centers, large spherical/ellipsoidal caverns are carved (30-60 block radius).
 * Spacing: ~500 blocks apart.
 */
public class CavernGenerator {

    private final FastNoiseLite cellNoise;
    private final FastNoiseLite floorNoise;
    private final boolean enabled;
    private final int seaLevel;
    private final int bedrockFloor;

    public CavernGenerator(long seed, ConfigSnapshot config) {
        this.enabled = config.enableMegaCaverns();
        this.seaLevel = config.seaLevel();
        this.bedrockFloor = config.worldMinY() + 30;

        // Linear Euclidean so the Distance return type is in its expected [0, ~1] range.
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

    public double sample(double x, double y, double z) {
        if (!enabled) return -1.0;
        if (y > seaLevel - 40) return -1.0;
        if (y < bedrockFloor) return -1.0;

        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);

        double cellDist = cellNoise.GetNoise(fx, fz);
        double cavernThreshold = 0.08;

        if (cellDist > cavernThreshold) return -1.0;

        double proximity = 1.0 - (cellDist / cavernThreshold);
        double cavernRadius = 25.0 + proximity * 35.0;

        double cavernCenterY = -100.0 + floorNoise.GetNoise(fx * 0.5f, fz * 0.5f) * 40.0;
        double verticalDist = Math.abs(y - cavernCenterY);
        double verticalRadius = cavernRadius * 0.6;

        if (verticalDist > verticalRadius) return -1.0;

        double verticalFade = 1.0 - (verticalDist / verticalRadius);

        double floorOffset = floorNoise.GetNoise(fx, (float) y, fz) * 8.0;
        double floorY = cavernCenterY - verticalRadius + 5.0 + floorOffset;

        if (y < floorY) return -1.0;

        return proximity * verticalFade - 0.2;
    }
}
