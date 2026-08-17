package com.grandterrain.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Immutable per-world generation settings.
 *
 * Serialized into the world via {@link #CODEC} (as the chunk generator's
 * "settings" field in level.dat) so a world always regenerates with the
 * settings it was created with — regardless of the config file on the
 * machine that later loads it. Without this, a world pre-generated with a
 * non-default preset would grow a terrain seam at the pre-gen boundary the
 * moment it was opened on a client with different (default) config.
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
        int biomeBlendWidth,
        float climateBlendWidth,
        int deepOceanOffset,
        int coastalOffset,
        int lowlandOffset,
        int cavernCenterY,
        boolean enableCastles,
        boolean enableRuins,
        boolean enableDungeons,
        boolean enableWatchtowers
) {
    /** Terrain / cave / river half of the record (DFU group() caps at 16 fields). */
    private record Shape(
            float mountainHeightScale, float continentalScale, float erosionStrength,
            int seaLevel, int worldMinY, int worldHeight,
            float caveDensity, float caveFrequency,
            boolean enableMegaCaverns, boolean enableUndergroundRivers,
            float riverWidth, float riverDepth) {
        static final Codec<Shape> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.fieldOf("mountain_height_scale").forGetter(Shape::mountainHeightScale),
                Codec.FLOAT.fieldOf("continental_scale").forGetter(Shape::continentalScale),
                Codec.FLOAT.fieldOf("erosion_strength").forGetter(Shape::erosionStrength),
                Codec.INT.fieldOf("sea_level").forGetter(Shape::seaLevel),
                Codec.INT.fieldOf("world_min_y").forGetter(Shape::worldMinY),
                Codec.INT.fieldOf("world_height").forGetter(Shape::worldHeight),
                Codec.FLOAT.fieldOf("cave_density").forGetter(Shape::caveDensity),
                Codec.FLOAT.fieldOf("cave_frequency").forGetter(Shape::caveFrequency),
                Codec.BOOL.fieldOf("enable_mega_caverns").forGetter(Shape::enableMegaCaverns),
                Codec.BOOL.fieldOf("enable_underground_rivers").forGetter(Shape::enableUndergroundRivers),
                Codec.FLOAT.fieldOf("river_width").forGetter(Shape::riverWidth),
                Codec.FLOAT.fieldOf("river_depth").forGetter(Shape::riverDepth)
        ).apply(i, Shape::new));
    }

    /** Climate / biome-threshold / structure half of the record. */
    private record Biomes(
            int snowLineBase, int biomeBlendWidth, float climateBlendWidth,
            int deepOceanOffset, int coastalOffset, int lowlandOffset,
            int cavernCenterY,
            boolean enableCastles, boolean enableRuins,
            boolean enableDungeons, boolean enableWatchtowers) {
        static final Codec<Biomes> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("snow_line_base").forGetter(Biomes::snowLineBase),
                Codec.INT.fieldOf("biome_blend_width").forGetter(Biomes::biomeBlendWidth),
                Codec.FLOAT.fieldOf("climate_blend_width").forGetter(Biomes::climateBlendWidth),
                Codec.INT.fieldOf("deep_ocean_offset").forGetter(Biomes::deepOceanOffset),
                Codec.INT.fieldOf("coastal_offset").forGetter(Biomes::coastalOffset),
                Codec.INT.fieldOf("lowland_offset").forGetter(Biomes::lowlandOffset),
                Codec.INT.fieldOf("cavern_center_y").forGetter(Biomes::cavernCenterY),
                Codec.BOOL.fieldOf("enable_castles").forGetter(Biomes::enableCastles),
                Codec.BOOL.fieldOf("enable_ruins").forGetter(Biomes::enableRuins),
                Codec.BOOL.fieldOf("enable_dungeons").forGetter(Biomes::enableDungeons),
                Codec.BOOL.fieldOf("enable_watchtowers").forGetter(Biomes::enableWatchtowers)
        ).apply(i, Biomes::new));
    }

    public static final Codec<ConfigSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
            Shape.CODEC.fieldOf("shape").forGetter(ConfigSnapshot::shape),
            Biomes.CODEC.fieldOf("biomes").forGetter(ConfigSnapshot::biomes)
    ).apply(i, ConfigSnapshot::of));

    private Shape shape() {
        return new Shape(mountainHeightScale, continentalScale, erosionStrength,
                seaLevel, worldMinY, worldHeight, caveDensity, caveFrequency,
                enableMegaCaverns, enableUndergroundRivers, riverWidth, riverDepth);
    }

    private Biomes biomes() {
        return new Biomes(snowLineBase, biomeBlendWidth, climateBlendWidth,
                deepOceanOffset, coastalOffset, lowlandOffset, cavernCenterY,
                enableCastles, enableRuins, enableDungeons, enableWatchtowers);
    }

    private static ConfigSnapshot of(Shape s, Biomes b) {
        return new ConfigSnapshot(
                s.mountainHeightScale, s.continentalScale, s.erosionStrength,
                s.seaLevel, s.worldMinY, s.worldHeight,
                s.caveDensity, s.caveFrequency,
                s.enableMegaCaverns, s.enableUndergroundRivers,
                s.riverWidth, s.riverDepth,
                b.snowLineBase, b.biomeBlendWidth, b.climateBlendWidth,
                b.deepOceanOffset, b.coastalOffset, b.lowlandOffset,
                b.cavernCenterY,
                b.enableCastles, b.enableRuins, b.enableDungeons, b.enableWatchtowers);
    }

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
                c.deepOceanOffset,
                c.coastalOffset,
                c.lowlandOffset,
                c.cavernCenterY,
                c.enableCastles,
                c.enableRuins,
                c.enableDungeons,
                c.enableWatchtowers
        );
    }
}
