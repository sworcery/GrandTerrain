package com.grandterrain.worldgen.noise;

import com.grandterrain.config.ConfigSnapshot;

/**
 * Generates continental-scale noise that defines whether terrain is ocean, coast,
 * inland, or deep continental (mountain territory).
 *
 * Output range: [-1, 1]
 *   < -0.3 : ocean
 *   -0.3 to 0.0 : coast/lowlands
 *   0.0 to 0.6 : inland
 *   > 0.6 : deep continental (mountain territory)
 */
public class ContinentalNoise {

    // Period at which float precision breaks down for noise (2^21 ~ 2M blocks).
    // Wrap coordinates modulo this to keep them near zero.
    private static final double COORD_WRAP = 2097152.0;

    private final FastNoiseLite noise;
    private final FastNoiseLite warpX;
    private final FastNoiseLite warpZ;
    private final float scale;

    public ContinentalNoise(long seed, ConfigSnapshot config) {
        this.scale = config.continentalScale();

        noise = new FastNoiseLite((int) seed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFractalOctaves(4);
        noise.SetFractalLacunarity(2.0f);
        noise.SetFractalGain(0.5f);
        noise.SetFrequency(1.0f / (12000.0f * scale));

        warpX = new FastNoiseLite((int) (seed ^ 0xA5A5A5A5L));
        warpX.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warpX.SetFractalType(FastNoiseLite.FractalType.FBm);
        warpX.SetFractalOctaves(3);
        warpX.SetFrequency(1.0f / 6000.0f);

        warpZ = new FastNoiseLite((int) (seed ^ 0x5A5A5A5AL));
        warpZ.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        warpZ.SetFractalType(FastNoiseLite.FractalType.FBm);
        warpZ.SetFractalOctaves(3);
        warpZ.SetFrequency(1.0f / 6000.0f);
    }

    /** Wrap doubles into a range where float precision is safe. */
    public static float wrapToFloat(double v) {
        double wrapped = v - Math.floor(v / COORD_WRAP) * COORD_WRAP;
        return (float) wrapped;
    }

    public double sample(double x, double z) {
        float fx = wrapToFloat(x);
        float fz = wrapToFloat(z);

        float warpAmount = 300.0f * scale;
        float wx = fx + warpX.GetNoise(fx, fz) * warpAmount;
        float wz = fz + warpZ.GetNoise(fx, fz) * warpAmount;

        return noise.GetNoise(wx, wz);
    }
}
