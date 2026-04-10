package com.grandterrain.config;

public class GrandterrainConfig {

    // Terrain
    public float mountainHeightScale = 5.0f;
    public float continentalScale = 1.0f;
    public float erosionStrength = 1.0f;
    public int seaLevel = 128;
    public int worldMinY = -256;
    public int worldHeight = 1024;

    // Caves
    public float caveDensity = 1.0f;
    public float caveFrequency = 1.0f;
    public boolean enableMegaCaverns = true;
    public boolean enableUndergroundRivers = true;

    // Rivers
    public float riverWidth = 1.0f;
    public float riverDepth = 1.0f;

    // Snow
    public int snowLineBase = 400;
    public float snowLineVariation = 50.0f;

    // Structures
    public float structureDensity = 1.0f;
    public boolean enableCastles = true;
    public boolean enableRuins = true;
    public boolean enableDungeons = true;

    // Biomes
    public boolean enableVolcanicRegions = true;

    /**
     * Validates and clamps all config values to safe ranges.
     * Prevents division by zero, negative dimensions, and other invalid states.
     */
    public void validate() {
        mountainHeightScale = clamp(mountainHeightScale, 1.0f, 10.0f);
        continentalScale = clamp(continentalScale, 0.1f, 5.0f);
        erosionStrength = clamp(erosionStrength, 0.0f, 3.0f);
        seaLevel = clamp(seaLevel, 32, 512);
        worldMinY = clamp(worldMinY, -2032, 0);
        worldHeight = clamp(worldHeight, 256, 4064);

        caveDensity = clamp(caveDensity, 0.0f, 5.0f);
        caveFrequency = clamp(caveFrequency, 0.1f, 5.0f);

        riverWidth = clamp(riverWidth, 0.1f, 5.0f);
        riverDepth = clamp(riverDepth, 0.1f, 5.0f);

        snowLineBase = clamp(snowLineBase, 100, 800);
        snowLineVariation = clamp(snowLineVariation, 0.0f, 200.0f);

        structureDensity = clamp(structureDensity, 0.0f, 10.0f);

        // Ensure sea level is within world bounds
        if (seaLevel < worldMinY + 10) seaLevel = worldMinY + 10;
        if (seaLevel > worldMinY + worldHeight - 10) seaLevel = worldMinY + worldHeight - 10;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
