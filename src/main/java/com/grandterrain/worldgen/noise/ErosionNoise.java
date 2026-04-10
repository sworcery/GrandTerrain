package com.grandterrain.worldgen.noise;

import com.grandterrain.config.GrandterrainConfig;

/**
 * Multi-layer noise approximating erosion effects:
 * 1. Macro erosion: wide valley systems and plateaus
 * 2. Voronoi-based valley carving: branching valley networks
 */
public class ErosionNoise {

    private final FastNoiseLite macroNoise;
    private final FastNoiseLite valleyNoise;
    private final FastNoiseLite detailNoise;
    private final float erosionStrength;

    public ErosionNoise(long seed, GrandterrainConfig config) {
        this.erosionStrength = config.erosionStrength;

        macroNoise = new FastNoiseLite((int) (seed + 7000));
        macroNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        macroNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        macroNoise.SetFractalOctaves(3);
        macroNoise.SetFrequency(1.0f / 4000.0f);

        valleyNoise = new FastNoiseLite((int) (seed + 8000));
        valleyNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        valleyNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq);
        valleyNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2Sub);
        valleyNoise.SetFrequency(1.0f / 800.0f);

        detailNoise = new FastNoiseLite((int) (seed + 9000));
        detailNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        detailNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        detailNoise.SetFractalOctaves(4);
        detailNoise.SetFrequency(1.0f / 200.0f);
    }

    /**
     * Returns the macro erosion value at the given position.
     * Range: [-1, 1]. High values = more eroded (flatter terrain).
     */
    public double sampleMacro(double x, double z) {
        return macroNoise.GetNoise((float) x, (float) z) * erosionStrength;
    }

    /**
     * Returns the valley carving depth at the given position.
     * Range: [0, 1]. Values near 0 indicate valley floors.
     */
    public double sampleValley(double x, double z) {
        float raw = valleyNoise.GetNoise((float) x, (float) z);
        // Cellular Distance2Sub returns values roughly in [-1, 1]
        // Transform so that edges (small values) become valleys
        double valley = 1.0 - Math.min(1.0, Math.max(0.0, (raw + 1.0) * 0.5));
        return valley * valley * erosionStrength;
    }

    /**
     * Returns the detail noise for surface variation.
     * Amplitude should be reduced on steep slopes to simulate erosion removing detail.
     */
    public double sampleDetail(double x, double z) {
        return detailNoise.GetNoise((float) x, (float) z);
    }
}
