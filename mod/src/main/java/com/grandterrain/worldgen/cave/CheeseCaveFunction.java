package com.grandterrain.worldgen.cave;

import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.noise.ContinentalNoise;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Generates large, irregular cavern spaces using 3D noise.
 * Named "cheese caves" because the terrain looks like Swiss cheese.
 * Cavern size increases with depth.
 */
public class CheeseCaveFunction implements CaveContributor {

    private final FastNoiseLite noise;
    private final FastNoiseLite scaleNoise;
    private final float density;
    private final int seaLevel;
    private final int bedrockFloor;
    private final int worldMinY;
    private final double depthNormalizer;  // (seaLevel - worldMinY) so depthFactor saturates at 1.0

    public CheeseCaveFunction(long seed, ConfigSnapshot config) {
        this.density = config.caveDensity();
        this.seaLevel = config.seaLevel();
        this.worldMinY = config.worldMinY();
        this.bedrockFloor = config.worldMinY() + 16;
        this.depthNormalizer = Math.max(1.0, seaLevel - worldMinY);

        noise = new FastNoiseLite((int) (seed ^ 0xCA7ECA7EL));
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFractalOctaves(3);
        noise.SetFractalLacunarity(2.0f);
        noise.SetFractalGain(0.5f);
        noise.SetFrequency(1.0f / 200.0f);

        scaleNoise = new FastNoiseLite((int) (seed ^ 0xCA5ECA5EL));
        scaleNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        scaleNoise.SetFrequency(1.0f / 400.0f);
    }

    @Override public int minY() { return worldMinY + 5; }
    @Override public int maxY() { return seaLevel - 10; }

    @Override
    public CarveResult sample(double x, double y, double z) {
        float fx = ContinentalNoise.wrapToFloat(x);
        float fy = (float) (y * 1.5);
        float fz = ContinentalNoise.wrapToFloat(z);

        double n = noise.GetNoise(fx, fy, fz);
        // Normalize depth by full world range so factor saturates at 1.0 near bedrock
        // regardless of configured worldMinY/seaLevel.
        double depthFactor = Math.max(0, Math.min(1.0, (seaLevel - y) / depthNormalizer));
        double threshold = 0.55 - depthFactor * 0.15 * density;
        threshold += scaleNoise.GetNoise(fx, fz) * 0.1;

        if (y > seaLevel - 10) {
            threshold += Math.max(0, (y - (seaLevel - 10)) / 20.0) * 2.0;
        }
        if (y < bedrockFloor) {
            threshold += Math.max(0, (bedrockFloor - y) / 16.0) * 2.0;
        }

        return (n - threshold) > 0 ? CarveResult.CARVE_AIR : CarveResult.SOLID;
    }
}
