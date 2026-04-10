package com.grandterrain.worldgen.noise;

import com.grandterrain.config.GrandterrainConfig;
import com.grandterrain.worldgen.cave.CavernGenerator;
import com.grandterrain.worldgen.cave.CheeseCaveFunction;
import com.grandterrain.worldgen.cave.SpaghettiCaveFunction;
import com.grandterrain.worldgen.cave.UndergroundRiverCarver;
import com.grandterrain.worldgen.river.RiverDensityFunction;

/**
 * Holds all the noise generators needed for world generation.
 * Created once per world with the world seed, then shared across chunk generation.
 */
public class GrandterrainNoiseRouter {

    private final TerrainDensityFunction terrainDensity;
    private final CheeseCaveFunction cheeseCaves;
    private final SpaghettiCaveFunction spaghettiCaves;
    private final CavernGenerator megaCaverns;
    private final UndergroundRiverCarver undergroundRivers;
    private final RiverDensityFunction surfaceRivers;
    private final GrandterrainConfig config;
    private final long seed;

    public GrandterrainNoiseRouter(long seed, GrandterrainConfig config) {
        this.seed = seed;
        this.config = config;
        this.terrainDensity = new TerrainDensityFunction(seed, config);
        this.cheeseCaves = new CheeseCaveFunction(seed, config);
        this.spaghettiCaves = new SpaghettiCaveFunction(seed, config);
        this.megaCaverns = new CavernGenerator(seed, config);
        this.undergroundRivers = new UndergroundRiverCarver(seed, config);
        this.surfaceRivers = new RiverDensityFunction(seed, config);
    }

    public TerrainDensityFunction getTerrainDensity() {
        return terrainDensity;
    }

    public CheeseCaveFunction getCheeseCaves() {
        return cheeseCaves;
    }

    public SpaghettiCaveFunction getSpaghettiCaves() {
        return spaghettiCaves;
    }

    public CavernGenerator getMegaCaverns() {
        return megaCaverns;
    }

    public UndergroundRiverCarver getUndergroundRivers() {
        return undergroundRivers;
    }

    public RiverDensityFunction getSurfaceRivers() {
        return surfaceRivers;
    }

    public GrandterrainConfig getConfig() {
        return config;
    }

    public long getSeed() {
        return seed;
    }

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
