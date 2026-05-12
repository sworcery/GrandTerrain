package com.grandterrain.worldgen.noise;

import com.grandterrain.config.ConfigSnapshot;

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

    public ErosionNoise(long seed, ConfigSnapshot config) {
        this.erosionStrength = config.erosionStrength();

        macroNoise = new FastNoiseLite((int) (seed ^ 0x12345678L));
        macroNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        macroNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        macroNoise.SetFractalOctaves(3);
        macroNoise.SetFrequency(1.0f / 4000.0f);

        // Use linear Euclidean distance (not squared) so Distance2Sub output
        // actually ranges [-1, 1] as thresholds assume.
        valleyNoise = new FastNoiseLite((int) (seed ^ 0x87654321L));
        valleyNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        valleyNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
        valleyNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2Sub);
        valleyNoise.SetFrequency(1.0f / 800.0f);

        detailNoise = new FastNoiseLite((int) (seed ^ 0xABCDEF01L));
        detailNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        detailNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        detailNoise.SetFractalOctaves(4);
        detailNoise.SetFrequency(1.0f / 200.0f);
    }

    /**
     * Returns the macro erosion value. Range: [-1, 1] * erosionStrength.
     * High values = more eroded (flatter terrain).
     */
    public double sampleMacro(double x, double z) {
        return macroNoise.GetNoise(ContinentalNoise.wrapToFloat(x),
                ContinentalNoise.wrapToFloat(z)) * erosionStrength;
    }

    /**
     * Returns the valley carving depth. Range: [0, 1] * erosionStrength.
     * Values near 1 indicate valley floors (close to Voronoi cell edges).
     */
    public double sampleValley(double x, double z) {
        float raw = valleyNoise.GetNoise(ContinentalNoise.wrapToFloat(x),
                ContinentalNoise.wrapToFloat(z));
        // Distance2Sub with linear Euclidean returns roughly [-1, 1].
        // Near-zero values are cell edges (the valleys we want).
        double absEdge = Math.min(1.0, Math.abs(raw));
        double valley = 1.0 - absEdge; // 1.0 at edge, 0.0 at center
        return valley * valley * erosionStrength;
    }

    /**
     * Returns the detail noise for surface variation. Range: [-1, 1].
     */
    public double sampleDetail(double x, double z) {
        return detailNoise.GetNoise(ContinentalNoise.wrapToFloat(x),
                ContinentalNoise.wrapToFloat(z));
    }
}
