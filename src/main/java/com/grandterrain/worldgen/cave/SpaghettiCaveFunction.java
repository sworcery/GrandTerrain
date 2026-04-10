package com.grandterrain.worldgen.cave;

import com.grandterrain.config.GrandterrainConfig;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Generates winding tunnel networks using the intersection of two 3D noise fields.
 * Where both fields are near zero simultaneously, a narrow tunnel is carved.
 */
public class SpaghettiCaveFunction {

    private final FastNoiseLite noiseA;
    private final FastNoiseLite noiseB;
    private final FastNoiseLite widthNoise;
    private final float frequency;
    private final int seaLevel;

    public SpaghettiCaveFunction(long seed, GrandterrainConfig config) {
        this.frequency = config.caveFrequency;
        this.seaLevel = config.seaLevel;

        noiseA = new FastNoiseLite((int) (seed + 21000));
        noiseA.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noiseA.SetFractalType(FastNoiseLite.FractalType.FBm);
        noiseA.SetFractalOctaves(4);
        noiseA.SetFractalLacunarity(2.0f);
        noiseA.SetFractalGain(0.5f);
        noiseA.SetFrequency(1.0f / (100.0f / frequency));

        noiseB = new FastNoiseLite((int) (seed + 22000));
        noiseB.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noiseB.SetFractalType(FastNoiseLite.FractalType.FBm);
        noiseB.SetFractalOctaves(4);
        noiseB.SetFractalLacunarity(2.0f);
        noiseB.SetFractalGain(0.5f);
        noiseB.SetFrequency(1.0f / (100.0f / frequency));

        // Width variation noise
        widthNoise = new FastNoiseLite((int) (seed + 23000));
        widthNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        widthNoise.SetFrequency(1.0f / 60.0f);
    }

    /**
     * Returns the tunnel carving value. Positive = carve (air), negative = solid.
     */
    public double sample(double x, double y, double z) {
        float fx = (float) x;
        float fy = (float) y;
        float fz = (float) z;

        double a = noiseA.GetNoise(fx, fy, fz);
        double b = noiseB.GetNoise(fx, fy * 0.8f, fz);

        // Width varies along the tunnel
        double width = 0.04 + (widthNoise.GetNoise(fx, fy, fz) + 1.0) * 0.02;

        // Both noise fields must be near zero for a tunnel
        double tunnelValue = width * width - (a * a + b * b);

        // Don't carve near the surface
        if (y > seaLevel - 5) {
            double surfaceBlend = Math.max(0, (y - (seaLevel - 5)) / 15.0);
            tunnelValue -= surfaceBlend * 0.5;
        }

        // Don't carve near bedrock
        if (y < -245) {
            double bedrockBlend = Math.max(0, (-245 - y) / 11.0);
            tunnelValue -= bedrockBlend * 0.5;
        }

        return tunnelValue;
    }
}
