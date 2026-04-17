package com.grandterrain.worldgen.noise;

import com.grandterrain.config.ConfigSnapshot;

/**
 * Ridged multifractal noise for dramatic mountain ridgelines and peaks.
 * Uses the formula: ridge = (1 - abs(noise))^2 with FBm-style octave stacking.
 *
 * Output range: [0, 1] where 1 = peak of ridge
 */
public class MountainRidgeNoise {

    private final FastNoiseLite[] octaveNoise;
    private final FastNoiseLite warpX;
    private final FastNoiseLite warpZ;
    private final int octaves;
    private final float persistence;
    private final float lacunarity;
    private final float baseFrequency;
    private final float heightScale;

    public MountainRidgeNoise(long seed, ConfigSnapshot config) {
        this.octaves = 7;
        this.persistence = 0.5f;
        this.lacunarity = 2.1f;
        this.baseFrequency = 1.0f / 2000.0f;
        this.heightScale = config.mountainHeightScale();

        octaveNoise = new FastNoiseLite[octaves];
        for (int i = 0; i < octaves; i++) {
            octaveNoise[i] = new FastNoiseLite((int) (seed ^ (0x9E3779B1L * (i + 1))));
            octaveNoise[i].SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
            octaveNoise[i].SetFrequency(1.0f);
        }

        warpX = new FastNoiseLite((int) (seed ^ 0xC3C3C3C3L));
        warpX.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warpX.SetFractalType(FastNoiseLite.FractalType.FBm);
        warpX.SetFractalOctaves(3);
        warpX.SetFrequency(1.0f / 3000.0f);

        warpZ = new FastNoiseLite((int) (seed ^ 0x3C3C3C3CL));
        warpZ.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warpZ.SetFractalType(FastNoiseLite.FractalType.FBm);
        warpZ.SetFractalOctaves(3);
        warpZ.SetFrequency(1.0f / 3000.0f);
    }

    public double sample(double x, double z) {
        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);

        float warpAmount = 150.0f;
        float origFx = fx;
        float origFz = fz;
        fx += warpX.GetNoise(origFx, origFz) * warpAmount;
        fz += warpZ.GetNoise(origFx, origFz) * warpAmount;

        double value = 0.0;
        double amplitude = 1.0;
        double frequency = baseFrequency;
        double maxAmplitude = 0.0;
        double weight = 1.0;

        for (int i = 0; i < octaves; i++) {
            double n = octaveNoise[i].GetNoise(fx * (float) frequency, fz * (float) frequency);

            n = 1.0 - Math.abs(n);
            n = n * n;

            n *= weight;
            weight = Math.min(1.0, Math.max(0.0, n * 2.0));

            value += n * amplitude;
            maxAmplitude += amplitude;

            frequency *= lacunarity;
            amplitude *= persistence;
        }

        return (value / maxAmplitude);
    }

    /**
     * Samples the ridge noise and scales it by the configured height multiplier.
     * Returns height contribution in blocks.
     */
    public double sampleHeight(double x, double z, double continentalBlend) {
        double ridge = sample(x, z);
        double blendedRidge = ridge * smoothstep(0.2, 0.6, continentalBlend);
        return blendedRidge * 128.0 * heightScale;
    }

    /** Pre-sampled variant: compute height from an already-known ridge value. */
    public double sampleHeightFromRidge(double ridge, double continentalBlend) {
        double blendedRidge = ridge * smoothstep(0.2, 0.6, continentalBlend);
        return blendedRidge * 128.0 * heightScale;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.min(1.0, Math.max(0.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
