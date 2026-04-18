package com.grandterrain.worldgen.noise;

import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.cave.CaveContributor;
import com.grandterrain.worldgen.cave.CavernGenerator;
import com.grandterrain.worldgen.cave.CheeseCaveFunction;
import com.grandterrain.worldgen.cave.SpaghettiCaveFunction;
import com.grandterrain.worldgen.cave.UndergroundRiverCarver;
import com.grandterrain.worldgen.river.RiverDensityFunction;

import java.util.List;

/**
 * Holds all the noise generators needed for world generation.
 * Created once per world with the world seed, then shared (read-only) across chunk generation.
 */
public class GrandterrainNoiseRouter {

    private final TerrainDensityFunction terrainDensity;
    private final List<CaveContributor> caves;
    private final RiverDensityFunction surfaceRivers;
    private final ConfigSnapshot config;
    private final long seed;

    public GrandterrainNoiseRouter(long seed, ConfigSnapshot config) {
        this.seed = seed;
        this.config = config;
        this.terrainDensity = new TerrainDensityFunction(seed, config);
        // Order matters: first non-SOLID result wins. Underground rivers run
        // first so water claims any block in its tight Y band before cheese/
        // spaghetti carves it to air. General cave types run after.
        this.caves = List.of(
                new UndergroundRiverCarver(seed, config),
                new CheeseCaveFunction(seed, config),
                new SpaghettiCaveFunction(seed, config),
                new CavernGenerator(seed, config)
        );
        this.surfaceRivers = new RiverDensityFunction(seed, config);
    }

    public TerrainDensityFunction getTerrainDensity() { return terrainDensity; }
    public List<CaveContributor> getCaves() { return caves; }
    public RiverDensityFunction getSurfaceRivers() { return surfaceRivers; }
    public ConfigSnapshot getConfig() { return config; }
    public long getSeed() { return seed; }

    public double sampleContinentalness(double x, double z) {
        return terrainDensity.getContinentalNoise().sample(x, z);
    }
    public double sampleRidges(double x, double z) {
        return terrainDensity.getMountainRidgeNoise().sample(x, z);
    }
    public double sampleErosion(double x, double z) {
        return terrainDensity.getErosionNoise().sampleMacro(x, z);
    }
}
