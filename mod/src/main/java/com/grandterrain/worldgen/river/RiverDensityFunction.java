package com.grandterrain.worldgen.river;

import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.noise.ContinentalNoise;
import com.grandterrain.worldgen.noise.FastNoiseLite;
import com.grandterrain.worldgen.noise.ValleyNetworkNoise;

/**
 * Surface rivers along the shared valley network (see ValleyNetworkNoise —
 * rivers run in the valleys that erosion carved, with matching meander).
 *
 * Three shaping controls keep the network from being a geometric honeycomb:
 *  - a low-frequency selector gates which valley edges actually carry water,
 *    tapering river width to zero instead of cutting hard;
 *  - carve depth fades with surface altitude (no rivers over snow peaks —
 *    high-altitude valleys keep their erosion carve but stay dry);
 *  - width varies along the run.
 *
 * Minimum carve depth of 1.5 blocks is enforced: columns merely "near" a
 * river do not get shallow 1-block ditches.
 */
public class RiverDensityFunction {

    /** Minimum carve depth in blocks. Columns below this threshold skip carving. */
    private static final double MIN_CARVE = 1.5;

    /** Base half-width of rivers in edge-distance units (measured metric). */
    private static final double BASE_WIDTH = 0.05;

    /** Selector gate: keep edges where selector noise exceeds this. */
    private static final double SELECTOR_CUTOFF = -0.15;
    private static final double SELECTOR_FADE = 0.35;

    /** Altitude fade band for river water (blocks above sea level). */
    private static final int DRY_FADE_START = 60;
    private static final int DRY_FADE_END = 220;

    private final ValleyNetworkNoise network;
    private final FastNoiseLite selectorNoise;
    private final FastNoiseLite elevationNoise;
    private final FastNoiseLite widthVariation;
    private final float riverWidth;
    private final float riverDepth;
    private final int seaLevel;

    public RiverDensityFunction(long seed, ConfigSnapshot config) {
        this.riverWidth = config.riverWidth();
        this.riverDepth = config.riverDepth();
        this.seaLevel = config.seaLevel();

        // Same network as ErosionNoise: rivers follow valley floors.
        network = new ValleyNetworkNoise(seed);

        selectorNoise = new FastNoiseLite((int) (seed ^ 0x5E1EC70FL));
        selectorNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        selectorNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        selectorNoise.SetFractalOctaves(2);
        selectorNoise.SetFrequency(1.0f / 2800.0f);

        elevationNoise = new FastNoiseLite((int) (seed ^ 0x21764E21L));
        elevationNoise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        elevationNoise.SetFractalType(FastNoiseLite.FractalType.FBm);
        elevationNoise.SetFractalOctaves(2);
        elevationNoise.SetFrequency(1.0f / 2000.0f);

        widthVariation = new FastNoiseLite((int) (seed ^ 0x21764E22L));
        widthVariation.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        widthVariation.SetFrequency(1.0f / 200.0f);
    }

    /** Smoothstep of t clamped to [0,1]. */
    private static double smooth01(double t) {
        if (t <= 0) return 0;
        if (t >= 1) return 1;
        return t * t * (3 - 2 * t);
    }

    /** Fraction [0,1] of this edge's water presence from the sparsity gate. */
    private double selectorGate(float fx, float fz) {
        double sel = selectorNoise.GetNoise(fx, fz);
        return smooth01((sel - SELECTOR_CUTOFF) / SELECTOR_FADE);
    }

    /**
     * Returns the river carving depth in blocks for this XZ position.
     * Zero if the column is outside a selected river or below minimum carve.
     */
    public double sampleRiverCarving(double x, double z) {
        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);

        double gate = selectorGate(fx, fz);
        if (gate <= 0) return 0.0;

        double edgeDist = network.edgeDistance01(x, z);

        double widthMod = 1.0 + widthVariation.GetNoise(fx, fz) * 0.4;
        double effectiveWidth = BASE_WIDTH * riverWidth * widthMod * gate;

        if (edgeDist > effectiveWidth) return 0.0;

        double centeredness = 1.0 - (edgeDist / effectiveWidth);
        double depth = centeredness * centeredness * 8.0 * riverDepth * (0.4 + 0.6 * gate);

        // Skip shallow edge ditches
        return depth < MIN_CARVE ? 0.0 : depth;
    }

    /**
     * Attenuate a carve depth by the surface altitude it applies at: full
     * strength near sea level, fading to zero well below the snow line so
     * rivers don't run over mountaintops. Returns 0 when the remaining carve
     * drops below the minimum.
     */
    public double attenuateForAltitude(double carve, double surfaceY) {
        double lo = seaLevel + DRY_FADE_START;
        double hi = seaLevel + DRY_FADE_END;
        if (surfaceY <= lo) return carve;
        if (surfaceY >= hi) return 0.0;
        double faded = carve * (1.0 - smooth01((surfaceY - lo) / (hi - lo)));
        return faded < MIN_CARVE ? 0.0 : faded;
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

    /** Check if this position is on a selected river path (ignores altitude). */
    public boolean isRiver(double x, double z) {
        float fx = ContinentalNoise.wrapToFloat(x);
        float fz = ContinentalNoise.wrapToFloat(z);
        double gate = selectorGate(fx, fz);
        if (gate <= 0) return false;
        return network.edgeDistance01(x, z) < 0.04 * riverWidth * gate;
    }
}
