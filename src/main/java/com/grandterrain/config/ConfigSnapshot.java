package com.grandterrain.config;

/**
 * Immutable snapshot of GrandterrainConfig used by worker threads during chunk generation.
 * Taken once per world at generator initialization so that concurrent GUI edits to the
 * mutable {@link GrandterrainConfig} never affect in-progress chunk generation.
 */
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
        float snowLineVariation,
        float structureDensity,
        boolean enableCastles,
        boolean enableRuins,
        boolean enableDungeons,
        boolean enableVolcanicRegions
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
                c.snowLineVariation,
                c.structureDensity,
                c.enableCastles,
                c.enableRuins,
                c.enableDungeons,
                c.enableVolcanicRegions
        );
    }
}
