package com.grandterrain.config;

public record ConfigSnapshot(
        float mountainHeightScale,
        float continentalScale,
        float erosionStrength,
        int seaLevel,
        int worldMinY,
        int worldHeight,
        float caveDensity,
        float caveFrequency,
        boolean enableMegaCaverns,
        boolean enableUndergroundRivers,
        float riverWidth,
        float riverDepth,
        int snowLineBase,
        int biomeBlendWidth,
        float climateBlendWidth,
        int cavernCenterY,
        boolean enableCastles,
        boolean enableRuins,
        boolean enableDungeons,
        boolean enableWatchtowers
) {
    public static ConfigSnapshot from(GrandterrainConfig c) {
        return new ConfigSnapshot(
                c.mountainHeightScale,
                c.continentalScale,
                c.erosionStrength,
                c.seaLevel,
                c.worldMinY,
                c.worldHeight,
                c.caveDensity,
                c.caveFrequency,
                c.enableMegaCaverns,
                c.enableUndergroundRivers,
                c.riverWidth,
                c.riverDepth,
                c.snowLineBase,
                c.biomeBlendWidth,
                c.climateBlendWidth,
                c.cavernCenterY,
                c.enableCastles,
                c.enableRuins,
                c.enableDungeons,
                c.enableWatchtowers
        );
    }
}
