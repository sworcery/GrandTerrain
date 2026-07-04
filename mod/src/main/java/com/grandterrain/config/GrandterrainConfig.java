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

    // Snow & Climate
    public int snowLineBase = 400;
    public int biomeBlendWidth = 4;
    public float climateBlendWidth = 0.05f;

    // Biome altitude thresholds (offsets from sea level)
    public int deepOceanOffset = -60;
    public int coastalOffset = -20;
    public int lowlandOffset = 10;

    // Caverns
    public int cavernCenterY = -100;

    // Structures
    public boolean enableCastles = true;
    public boolean enableRuins = true;
    public boolean enableDungeons = true;
    public boolean enableWatchtowers = true;

    public void validate() {
        mountainHeightScale = clamp(mountainHeightScale, 1.0f, 10.0f);
        continentalScale = clamp(continentalScale, 0.1f, 5.0f);
        erosionStrength = clamp(erosionStrength, 0.0f, 3.0f);
        seaLevel = clamp(seaLevel, 32, 512);
        // World bounds are fixed by data/grandterrain/dimension_type/grandterrain.json.
        // Datapack dimension types are static, so config cannot drive them; pin the
        // values so the generator's reported bounds always match the real dimension.
        worldMinY = -256;
        worldHeight = 1024;

        caveDensity = clamp(caveDensity, 0.0f, 5.0f);
        caveFrequency = clamp(caveFrequency, 0.1f, 5.0f);

        riverWidth = clamp(riverWidth, 0.1f, 5.0f);
        riverDepth = clamp(riverDepth, 0.1f, 5.0f);

        snowLineBase = clamp(snowLineBase, 100, 800);
        biomeBlendWidth = clamp(biomeBlendWidth, 0, 12);
        climateBlendWidth = clamp(climateBlendWidth, 0.0f, 0.2f);
        deepOceanOffset = clamp(deepOceanOffset, -200, -10);
        coastalOffset = clamp(coastalOffset, -100, 0);
        lowlandOffset = clamp(lowlandOffset, 0, 100);
        if (coastalOffset < deepOceanOffset + 5) coastalOffset = deepOceanOffset + 5;
        if (lowlandOffset < coastalOffset + 5) lowlandOffset = coastalOffset + 5;
        coastalOffset = clamp(coastalOffset, -200, 0);
        lowlandOffset = clamp(lowlandOffset, 0, 200);
        // Keep the cavern band above the bedrock floor at worldMinY (-256).
        cavernCenterY = clamp(cavernCenterY, -250, 0);

        if (seaLevel < worldMinY + 10) seaLevel = worldMinY + 10;
        if (seaLevel > worldMinY + worldHeight - 10) seaLevel = worldMinY + worldHeight - 10;

        if (snowLineBase <= seaLevel + 50) snowLineBase = seaLevel + 100;
        if (snowLineBase > worldMinY + worldHeight - 5) {
            snowLineBase = worldMinY + worldHeight - 5;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
