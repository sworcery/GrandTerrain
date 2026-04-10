package com.grandterrain.worldgen.cave;

import com.grandterrain.config.GrandterrainConfig;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Generates large, irregular cavern spaces using 3D noise.
 * Named "cheese caves" because the terrain looks like Swiss cheese.
 * Cavern size increases with depth.
 */
public class CheeseCaveFunction {

    private final FastNoiseLite noise;
    private final FastNoiseLite scaleNoise;
    private final float density;
    private final int seaLevel;

    public CheeseCaveFunction(long seed, GrandterrainConfig config) {
        this.density = config.caveDensity;
        this.seaLevel = config.seaLevel;

        noise = new FastNoiseLite((int) (seed + 20000));
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFractalOctaves(3);
        noise.SetFractalLacunarity(2.0f);
        noise.SetFractalGain(0.5f);
        noise.SetFrequency(1.0f / 200.0f);

        // Secondary noise to vary cavern size
        scaleNoise = new FastNoiseLite((int) (seed + 20100));
        scaleNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        scaleNoise.SetFrequency(1.0f / 400.0f);
    }

    /**
     * Returns the cave carving value. Positive = carve (air), negative = solid.
     */
    public double sample(double x, double y, double z) {
        // Vertically squished for wider caves
        float fx = (float) x;
        float fy = (float) (y * 1.5);
        float fz = (float) z;

        double n = noise.GetNoise(fx, fy, fz);

        // Threshold varies with depth - deeper = larger caverns
        double depthFactor = Math.max(0, (seaLevel - y) / 300.0);
        double threshold = 0.55 - depthFactor * 0.15 * density;

        // Scale modifier for local variation
        double scale = scaleNoise.GetNoise(fx, fz) * 0.1;
        threshold += scale;

        // Don't carve near the surface (leave top 20 blocks solid)
        if (y > seaLevel - 10) {
            double surfaceBlend = Math.max(0, (y - (seaLevel - 10)) / 20.0);
            threshold += surfaceBlend * 2.0;
        }

        // Don't carve near bedrock
        if (y < -240) {
            double bedrockBlend = Math.max(0, (-240 - y) / 16.0);
            threshold += bedrockBlend * 2.0;
        }

        return n - threshold;
    }
}
