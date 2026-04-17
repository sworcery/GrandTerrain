package com.grandterrain.worldgen.cave;

import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.noise.ContinentalNoise;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Generates winding tunnel networks using the intersection of two 3D noise fields.
 * Where both fields are near zero simultaneously, a narrow tunnel is carved.
 *
 * Exits early near the surface and near bedrock to preserve those layers.
 */
public class SpaghettiCaveFunction {

    private final FastNoiseLite noiseA;
    private final FastNoiseLite noiseB;
    private final FastNoiseLite widthNoise;
    private final int seaLevel;
    private final int bedrockFloor;

    public SpaghettiCaveFunction(long seed, ConfigSnapshot config) {
        float frequency = config.caveFrequency();
        this.seaLevel = config.seaLevel();
        this.bedrockFloor = config.worldMinY() + 16;

        noiseA = new FastNoiseLite((int) (seed ^ 0x5A6E770AL));
        noiseA.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noiseA.SetFractalType(FastNoiseLite.FractalType.FBm);
        noiseA.SetFractalOctaves(4);
        noiseA.SetFractalLacunarity(2.0f);
        noiseA.SetFractalGain(0.5f);
        noiseA.SetFrequency(1.0f / (100.0f / frequency));

        noiseB = new FastNoiseLite((int) (seed ^ 0x5A6E770BL));
        noiseB.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noiseB.SetFractalType(FastNoiseLite.FractalType.FBm);
        noiseB.SetFractalOctaves(4);
        noiseB.SetFractalLacunarity(2.0f);
        noiseB.SetFractalGain(0.5f);
        noiseB.SetFrequency(1.0f / (100.0f / frequency));

        widthNoise = new FastNoiseLite((int) (seed ^ 0x5A6E770CL));
        widthNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        widthNoise.SetFrequency(1.0f / 60.0f);
    }

    public double sample(double x, double y, double z) {
        float fx = ContinentalNoise.wrapToFloat(x);
        float fy = (float) y;
        float fz = ContinentalNoise.wrapToFloat(z);

        double a = noiseA.GetNoise(fx, fy, fz);
        double b = noiseB.GetNoise(fx, fy * 0.8f, fz);

        double width = 0.04 + (widthNoise.GetNoise(fx, fy, fz) + 1.0) * 0.02;
        double tunnelValue = width * width - (a * a + b * b);

        if (y > seaLevel - 5) {
            double surfaceBlend = Math.max(0, (y - (seaLevel - 5)) / 15.0);
            tunnelValue -= surfaceBlend * 0.5;
        }

        if (y < bedrockFloor) {
            double bedrockBlend = Math.max(0, (bedrockFloor - y) / 11.0);
            tunnelValue -= bedrockBlend * 0.5;
        }

        return tunnelValue;
    }
}
