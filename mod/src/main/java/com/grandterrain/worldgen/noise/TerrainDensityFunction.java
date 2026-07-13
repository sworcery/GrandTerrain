package com.grandterrain.worldgen.noise;

import com.grandterrain.config.ConfigSnapshot;

/**
 * The core terrain shape function that combines all noise layers into a final
 * density value. Positive = solid block, negative = air.
 */
public class TerrainDensityFunction {

    private final ContinentalNoise continentalNoise;
    private final MountainRidgeNoise mountainRidgeNoise;
    private final ErosionNoise erosionNoise;
    private final FastNoiseLite baseTerrainNoise;
    private final ConfigSnapshot config;

    /** Per-thread cached slope value for the last XZ column sampled. */
    private static final class SlopeCache {
        long keyX = 1L;  // non-zero sentinel (doubleToRawLongBits of 0.0 is 0)
        long keyZ = 1L;
        boolean valid;
        double value = 1.0;
    }
    private final ThreadLocal<SlopeCache> slopeCache = ThreadLocal.withInitial(SlopeCache::new);

    public TerrainDensityFunction(long seed, ConfigSnapshot config) {
        this.config = config;
        this.continentalNoise = new ContinentalNoise(seed, config);
        this.mountainRidgeNoise = new MountainRidgeNoise(seed, config);
        this.erosionNoise = new ErosionNoise(seed, config);

        baseTerrainNoise = new FastNoiseLite((int) (seed ^ 0xF00DBEEFL));
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

        // Slope attenuation cached per XZ column; passes through pre-computed continental
        // to avoid the 4 redundant continental+mountain evaluations inside the slope calc.
        double slopeAttenuation = getCachedSlopeAttenuation(x, z);
        double detailContribution = detail * 15.0 * slopeAttenuation;

        double baseTerrain = baseTerrainNoise.GetNoise(
                ContinentalNoise.wrapToFloat(x),
                ContinentalNoise.wrapToFloat(z)) * 30.0;

        double surfaceHeight = softCeiling(
                baseHeight + mountainHeight - valleyDepth + detailContribution + baseTerrain);
        double density = surfaceHeight - y;

        if (y < config.worldMinY() + 8) {
            density += (config.worldMinY() + 8 - y) * 2.0;
        }

        return density;
    }

    /**
     * Compute just the surface height (no Y component). Useful for buildSurface and
     * getBaseHeight without the cost of the depth solve.
     */
    public double computeSurfaceHeight(double x, double z) {
        double continental = continentalNoise.sample(x, z);
        double baseHeight = computeBaseHeight(continental);
        double mountainHeight = mountainRidgeNoise.sampleHeight(x, z, continental);
        double erosionMacro = erosionNoise.sampleMacro(x, z);
        double valleyCarve = erosionNoise.sampleValley(x, z);
        double valleyDepth = valleyCarve * 80.0 * Math.max(0, 0.5 + erosionMacro);
        double detail = erosionNoise.sampleDetail(x, z);
        double slopeAttenuation = getCachedSlopeAttenuation(x, z);
        double detailContribution = detail * 15.0 * slopeAttenuation;
        double baseTerrain = baseTerrainNoise.GetNoise(
                ContinentalNoise.wrapToFloat(x),
                ContinentalNoise.wrapToFloat(z)) * 30.0;
        return softCeiling(baseHeight + mountainHeight - valleyDepth + detailContribution + baseTerrain);
    }

    /**
     * Asymptotically compress heights approaching the world ceiling. At the
     * default mountain scale the raw sum can exceed worldMinY + worldHeight
     * (measured max ~805 vs a 768 ceiling), which would clip peaks into flat
     * mesas at the top of the world. Compression starts 120 blocks below the
     * ceiling and approaches (ceiling - 20) but never reaches it, preserving
     * peak shape. C1-continuous at the transition (slope 1 at start).
     */
    private double softCeiling(double h) {
        double top = config.worldMinY() + config.worldHeight();
        double start = top - 120.0;
        if (h <= start) return h;
        double excess = h - start;
        double span = 100.0;
        return start + span * (excess / (excess + span));
    }

    private double getCachedSlopeAttenuation(double x, double z) {
        // Normalize -0.0 to 0.0 and avoid NaN issues
        long kx = Double.doubleToLongBits(x == 0.0 ? 0.0 : x);
        long kz = Double.doubleToLongBits(z == 0.0 ? 0.0 : z);
        SlopeCache cache = slopeCache.get();

        if (cache.valid && cache.keyX == kx && cache.keyZ == kz) {
            return cache.value;
        }

        double result = computeSlopeAttenuation(x, z);
        cache.keyX = kx;
        cache.keyZ = kz;
        cache.value = result;
        cache.valid = true;
        return result;
    }

    private double computeBaseHeight(double continental) {
        double seaLevel = config.seaLevel();

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

    private double computeSlopeAttenuation(double x, double z) {
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

    public ContinentalNoise getContinentalNoise() { return continentalNoise; }
    public MountainRidgeNoise getMountainRidgeNoise() { return mountainRidgeNoise; }
    public ErosionNoise getErosionNoise() { return erosionNoise; }
}
