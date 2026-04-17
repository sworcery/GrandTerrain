package com.grandterrain.worldgen.river;

import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.noise.ContinentalNoise;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Generates surface river paths using Voronoi edge detection.
 * Rivers carve channels into the terrain density, creating natural waterways.
 *
 * Minimum carve depth of 1 block is enforced: columns that are merely "near" a river
 * do not get shallow 1-block ditches - only columns with centeredness above threshold carve.
 */
public class RiverDensityFunction {

    /** Minimum carve depth in blocks. Columns below this threshold skip carving. */
    private static final double MIN_CARVE = 1.5;

    private final FastNoiseLite edgeNoise;
    private final FastNoiseLite elevationNoise;
    private final FastNoiseLite widthVariation;
    private final float riverWidth;
    private final float riverDepth;
    private final int seaLevel;

    public RiverDensityFunction(long seed, ConfigSnapshot config) {
        this.riverWidth = config.riverWidth();
        this.riverDepth = config.riverDepth();
        this.seaLevel = config.seaLevel();

        // Linear Euclidean so Distance2Sub is in its documented range.
        edgeNoise = new FastNoiseLite((int) (seed ^ 0x21764E20L));
        edgeNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        edgeNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.Euclidean);
        edgeNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2Sub);
        edgeNoise.SetFrequency(1.0f / 800.0f);

        elevationNoise = new FastNoiseLite((int) (seed ^ 0x21764E21L));
        elevationNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        elevationNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        elevationNoise.SetFractalOctaves(2);
        elevationNoise.SetFrequency(1.0f / 2000.0f);

        widthVariation = new FastNoiseLite((int) (seed ^ 0x21764E22L));
        widthVariation.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        widthVariation.SetFrequency(1.0f / 200.0f);
    }

    /**
     * Returns the river carving depth in blocks for this XZ position.
     * Zero if column is outside the river or below minimum carve threshold.
     */
    public double sampleRiverCarving(double x, double z) {
        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);

        double edge = edgeNoise.GetNoise(fx, fz);
        double edgeDist = Math.abs(edge);

        double widthMod = 1.0 + widthVariation.GetNoise(fx, fz) * 0.4;
        double effectiveWidth = 0.08 * riverWidth * widthMod;

        if (edgeDist > effectiveWidth) return 0.0;

        double centeredness = 1.0 - (edgeDist / effectiveWidth);
        double depth = centeredness * centeredness * 8.0 * riverDepth;

        // Skip shallow edge ditches
        return depth < MIN_CARVE ? 0.0 : depth;
    }

    /**
     * Returns the river water level. Below the terrain surface (it's carved into it),
     * and not allowed to fall below the channel floor the caller will carve.
     */
    public double getRiverWaterLevel(double x, double z, double terrainHeight, int carveDepth) {
        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);

        double elevation = elevationNoise.GetNoise(fx, fz);
        double desiredLevel = seaLevel + elevation * 30.0;

        // Clamp between the channel floor and the terrain surface.
        double channelFloor = terrainHeight - carveDepth;
        double channelTop = terrainHeight;
        return Math.max(channelFloor, Math.min(channelTop, desiredLevel));
    }

    /** Check if this position is on a river path (for biome placement). */
    public boolean isRiver(double x, double z) {
        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);
        double edge = edgeNoise.GetNoise(fx, fz);
        return Math.abs(edge) < 0.06 * riverWidth;
    }
}
