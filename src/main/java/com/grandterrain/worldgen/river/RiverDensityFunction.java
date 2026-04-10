package com.grandterrain.worldgen.river;

import com.grandterrain.config.GrandterrainConfig;
import com.grandterrain.worldgen.noise.FastNoiseLite;

/**
 * Generates surface river paths using Voronoi edge detection.
 * Rivers carve channels into the terrain density, creating natural waterways.
 */
public class RiverDensityFunction {

    private final FastNoiseLite edgeNoise;
    private final FastNoiseLite elevationNoise;
    private final FastNoiseLite widthVariation;
    private final float riverWidth;
    private final float riverDepth;
    private final int seaLevel;

    public RiverDensityFunction(long seed, GrandterrainConfig config) {
        this.riverWidth = config.riverWidth;
        this.riverDepth = config.riverDepth;
        this.seaLevel = config.seaLevel;

        edgeNoise = new FastNoiseLite((int) (seed + 30000));
        edgeNoise.SetNoiseType(FastNoiseLite.NoiseType.Cellular);
        edgeNoise.SetCellularDistanceFunction(FastNoiseLite.CellularDistanceFunction.EuclideanSq);
        edgeNoise.SetCellularReturnType(FastNoiseLite.CellularReturnType.Distance2Sub);
        edgeNoise.SetFrequency(1.0f / 800.0f);

        elevationNoise = new FastNoiseLite((int) (seed + 30100));
        elevationNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        elevationNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        elevationNoise.SetFractalOctaves(2);
        elevationNoise.SetFrequency(1.0f / 2000.0f);

        widthVariation = new FastNoiseLite((int) (seed + 30200));
        widthVariation.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        widthVariation.SetFrequency(1.0f / 200.0f);
    }

    /**
     * Returns the river carving depth at this XZ position.
     * Positive values = carve this many blocks down from the terrain surface.
     * Zero or negative = no river here.
     */
    public double sampleRiverCarving(double x, double z) {
        float fx = (float) x;
        float fz = (float) z;

        double edge = edgeNoise.GetNoise(fx, fz);
        double edgeDist = Math.abs(edge);

        // Width varies along river
        double widthMod = 1.0 + widthVariation.GetNoise(fx, fz) * 0.4;
        double effectiveWidth = 0.08 * riverWidth * widthMod;

        if (edgeDist > effectiveWidth) return 0.0;

        // How centered we are on the river (1.0 = center, 0.0 = edge)
        double centeredness = 1.0 - (edgeDist / effectiveWidth);

        // River depth: deeper at center, shallower at edges
        double depth = centeredness * centeredness * 8.0 * riverDepth;

        return depth;
    }

    /**
     * Returns the river water level at this XZ position.
     * Rivers below sea level are submerged; rivers above have flowing water.
     */
    public double getRiverWaterLevel(double x, double z, double terrainHeight) {
        float fx = (float) x;
        float fz = (float) z;

        // Gradual elevation change along river
        double elevation = elevationNoise.GetNoise(fx, fz);

        // Water level follows terrain but stays mostly level
        double waterLevel = Math.min(terrainHeight, seaLevel + elevation * 30.0);

        return waterLevel;
    }

    /**
     * Check if this position is on a river path (for biome placement).
     */
    public boolean isRiver(double x, double z) {
        float fx = (float) x;
        float fz = (float) z;
        double edge = edgeNoise.GetNoise(fx, fz);
        return Math.abs(edge) < 0.06 * riverWidth;
    }
}
