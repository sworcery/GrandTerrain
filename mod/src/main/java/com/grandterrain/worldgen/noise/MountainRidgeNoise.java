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
        // 6 octaves: octave 7 (23-block wavelength) contributed 0.62 blocks mean
        // at default scale — below the ±15-block detail and ±30-block base
        // terrain terms layered on top — for 1/7 of the loop cost (measured).
        this.octaves = 6;
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
        // Single-octave warps: at a 3000-block warp wavelength the extra FBm
        // octaves were visually indistinguishable but were 46% of sample() cost.
        warpX.SetFrequency(1.0f / 3000.0f);

        warpZ = new FastNoiseLite((int) (seed ^ 0x3C3C3C3CL));
        warpZ.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
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

            // Hand-rolled ridged multifractal, NOT FastNoiseLite's FractalType.Ridged.
            // Measured output of sample() is [0.005, 0.96] (median 0.38), which is
            // what the *128*heightScale mapping assumes. FNL's Ridged fractal maps
            // to [-1,+1] (~14% negative) — switching to it would give ~14% of
            // inland columns a NEGATIVE mountain contribution. Do not "modernise".
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
    /** @param continental raw continental noise in [-1,1]; mountains gate in at 0.2..0.6 (inland only). */
    public double sampleHeight(double x, double z, double continental) {
        double ridge = sample(x, z);
        double blendedRidge = ridge * smoothstep(0.2, 0.6, continental);
        return blendedRidge * 128.0 * heightScale;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.min(1.0, Math.max(0.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
