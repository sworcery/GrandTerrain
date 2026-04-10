package com.grandterrain.worldgen.noise;

import com.grandterrain.config.GrandterrainConfig;

/**
 * The core terrain shape function that combines all noise layers into a final
 * density value. Positive = solid block, negative = air.
 */
public class TerrainDensityFunction {

    private final ContinentalNoise continentalNoise;
    private final MountainRidgeNoise mountainRidgeNoise;
    private final ErosionNoise erosionNoise;
    private final FastNoiseLite baseTerrainNoise;
    private final GrandterrainConfig config;

    // HIGH-3 fix: cache slope attenuation per XZ column (thread-local for safety)
    private final ThreadLocal<long[]> cachedSlopeKey = ThreadLocal.withInitial(() -> new long[]{Long.MIN_VALUE, Long.MIN_VALUE});
    private final ThreadLocal<double[]> cachedSlopeValue = ThreadLocal.withInitial(() -> new double[]{1.0});

    public TerrainDensityFunction(long seed, GrandterrainConfig config) {
        this.config = config;
        this.continentalNoise = new ContinentalNoise(seed, config);
        this.mountainRidgeNoise = new MountainRidgeNoise(seed, config);
        this.erosionNoise = new ErosionNoise(seed, config);

        baseTerrainNoise = new FastNoiseLite((int) (seed + 10000));
        baseTerrainNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        baseTerrainNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        baseTerrainNoise.SetFractalOctaves(3);
        baseTerrainNoise.SetFractalLacunarity(2.0f);
        baseTerrainNoise.SetFractalGain(0.5f);
        baseTerrainNoise.SetFrequency(1.0f / 400.0f);
    }

    /**
     * Compute the terrain density at the given world coordinates.
     *
     * @return positive if solid, negative if air
     */
    public double compute(double x, double y, double z) {
        double continental = continentalNoise.sample(x, z);
        double baseHeight = computeBaseHeight(continental);
        double mountainHeight = mountainRidgeNoise.sampleHeight(x, z, continental);

        double erosionMacro = erosionNoise.sampleMacro(x, z);
        double valleyCarve = erosionNoise.sampleValley(x, z);
        double valleyDepth = valleyCarve * 80.0 * Math.max(0, 0.5 + erosionMacro);

        double detail = erosionNoise.sampleDetail(x, z);

        // HIGH-3 fix: cache slope attenuation per XZ column (slope is 2D, doesn't vary with Y)
        double slopeAttenuation = getCachedSlopeAttenuation(x, z, baseHeight + mountainHeight);
        double detailContribution = detail * 15.0 * slopeAttenuation;

        double baseTerrain = baseTerrainNoise.GetNoise((float) x, (float) z) * 30.0;

        double surfaceHeight = baseHeight + mountainHeight - valleyDepth + detailContribution + baseTerrain;
        double density = surfaceHeight - y;

        if (y < config.worldMinY + 8) {
            density += (config.worldMinY + 8 - y) * 2.0;
        }

        return density;
    }

    private double getCachedSlopeAttenuation(double x, double z, double centerHeight) {
        long kx = Double.doubleToLongBits(x);
        long kz = Double.doubleToLongBits(z);
        long[] key = cachedSlopeKey.get();
        double[] val = cachedSlopeValue.get();

        if (key[0] == kx && key[1] == kz) {
            return val[0];
        }

        double result = computeSlopeAttenuation(x, z, centerHeight);
        key[0] = kx;
        key[1] = kz;
        val[0] = result;
        return result;
    }

    private double computeBaseHeight(double continental) {
        double seaLevel = config.seaLevel;

        if (continental < -0.3) {
            double oceanDepth = (-0.3 - continental) / 0.7;
            return seaLevel - 30 - oceanDepth * 80;
        } else if (continental < 0.0) {
            double coastBlend = (continental + 0.3) / 0.3;
            return seaLevel - 30 + coastBlend * 40;
        } else if (continental < 0.3) {
            double lowlandBlend = continental / 0.3;
            return seaLevel + 10 + lowlandBlend * 30;
        } else {
            double inlandBlend = Math.min(1.0, (continental - 0.3) / 0.4);
            return seaLevel + 40 + inlandBlend * 60;
        }
    }

    private double computeSlopeAttenuation(double x, double z, double centerHeight) {
        double sampleDist = 8.0;
        double hNorth = computeHeightFast(x, z - sampleDist);
        double hSouth = computeHeightFast(x, z + sampleDist);
        double hWest = computeHeightFast(x - sampleDist, z);
        double hEast = computeHeightFast(x + sampleDist, z);

        double slopeX = Math.abs(hEast - hWest) / (sampleDist * 2.0);
        double slopeZ = Math.abs(hNorth - hSouth) / (sampleDist * 2.0);
        double slope = Math.sqrt(slopeX * slopeX + slopeZ * slopeZ);

        return Math.max(0.1, 1.0 - slope * 0.5);
    }

    private double computeHeightFast(double x, double z) {
        double continental = continentalNoise.sample(x, z);
        double baseHeight = computeBaseHeight(continental);
        double mountainHeight = mountainRidgeNoise.sampleHeight(x, z, continental);
        return baseHeight + mountainHeight;
    }

    public ContinentalNoise getContinentalNoise() {
        return continentalNoise;
    }

    public MountainRidgeNoise getMountainRidgeNoise() {
        return mountainRidgeNoise;
    }

    public ErosionNoise getErosionNoise() {
        return erosionNoise;
    }
}
