package com.grandterrain.worldgen.noise;

import com.grandterrain.config.ConfigSnapshot;

/**
 * Multi-layer noise approximating erosion effects:
 * 1. Macro erosion: wide valley systems and plateaus
 * 2. Voronoi-based valley carving: branching valley networks
 */
public class ErosionNoise {

    private final FastNoiseLite macroNoise;
    private final ValleyNetworkNoise valleyNetwork;
    private final FastNoiseLite detailNoise;
    private final float erosionStrength;

    public ErosionNoise(long seed, ConfigSnapshot config) {
        this.erosionStrength = config.erosionStrength();

        macroNoise = new FastNoiseLite((int) (seed ^ 0x12345678L));
        macroNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        macroNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        macroNoise.SetFractalOctaves(3);
        macroNoise.SetFrequency(1.0f / 4000.0f);

        // Shared with RiverDensityFunction so rivers follow valley floors.
        valleyNetwork = new ValleyNetworkNoise(seed);

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
    /** Half-width of the valley influence zone in edge-distance units. */
    private static final double VALLEY_WIDTH = 0.35;

    public double sampleValley(double x, double z) {
        // 0 on the warped valley network, growing into cell interiors (the old
        // code treated FNL's -1-at-edge output as 0-at-edge, which made
        // "valleys" round pits at cell centers instead of connected networks).
        double edge01 = valleyNetwork.edgeDistance01(x, z);
        double valley = Math.max(0.0, 1.0 - edge01 / VALLEY_WIDTH); // 1 at edge
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
