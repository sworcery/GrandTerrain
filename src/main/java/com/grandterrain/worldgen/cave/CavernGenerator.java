package com.grandterrain.worldgen.cave;

import com.grandterrain.config.GrandterrainConfig;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Generates rare mega-caverns using Voronoi cellular noise.
 * At cell centers, large spherical/ellipsoidal caverns are carved (30-80 blocks diameter).
 * Spacing: ~500 blocks apart.
 */
public class CavernGenerator {

    private final FastNoiseLite cellNoise;
    private final FastNoiseLite floorNoise;
    private final boolean enabled;
    private final int seaLevel;

    public CavernGenerator(long seed, GrandterrainConfig config) {
        this.enabled = config.enableMegaCaverns;
        this.seaLevel = config.seaLevel;

        cellNoise = new FastNoiseLite((int) (seed + 24000));
        cellNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        cellNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq);
        cellNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance);
        cellNoise.SetFrequency(1.0f / 500.0f);

        floorNoise = new FastNoiseLite((int) (seed + 24100));
        floorNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        floorNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        floorNoise.SetFractalOctaves(3);
        floorNoise.SetFrequency(1.0f / 30.0f);
    }

    /**
     * Returns the mega-cavern carving value. Positive = carve, negative = solid.
     */
    public double sample(double x, double y, double z) {
        if (!enabled) return -1.0;

        // Only generate below a certain depth
        if (y > seaLevel - 40) return -1.0;
        if (y < -230) return -1.0;

        float fx = (float) x;
        float fz = (float) z;

        // Distance to nearest cell center (2D - caverns are column-shaped in XZ)
        double cellDist = cellNoise.GetNoise(fx, fz);

        // cellDist is in [0, ~1] range from Cellular Distance return type
        // Small values = close to cell center = cavern location
        double cavernThreshold = 0.08;

        if (cellDist > cavernThreshold) return -1.0;

        // Cavern shape: ellipsoidal
        double proximity = 1.0 - (cellDist / cavernThreshold);
        double cavernRadius = 25.0 + proximity * 35.0; // 25-60 block radius

        // Vertical extent: center the cavern around Y=-100
        double cavernCenterY = -100.0 + floorNoise.GetNoise(fx * 0.5f, fz * 0.5f) * 40.0;
        double verticalDist = Math.abs(y - cavernCenterY);
        double verticalRadius = cavernRadius * 0.6; // Flatter than wide

        if (verticalDist > verticalRadius) return -1.0;

        // Smooth edges
        double verticalFade = 1.0 - (verticalDist / verticalRadius);

        // Uneven floor using noise
        double floorOffset = floorNoise.GetNoise(fx, (float) y, fz) * 8.0;
        double floorY = cavernCenterY - verticalRadius + 5.0 + floorOffset;

        if (y < floorY) return -1.0;

        return proximity * verticalFade - 0.2;
    }
}
