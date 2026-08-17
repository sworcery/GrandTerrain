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
 * NOTE: every field below is REQUIRED by the codec, so a world saved by
 * this version fails to load if a field is later removed. Any NEW field
 * must use optionalFieldOf(name, default) or it will break existing worlds.
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

    /**
     * Decoded settings are validated exactly like the config file: a
     * hand-edited level.dat or a datapack could otherwise feed unclamped
     * values (or a world height disagreeing with the dimension type)
     * straight into the noise router.
     */
    private static ConfigSnapshot of(Shape s, Biomes b) {
        GrandterrainConfig c = new GrandterrainConfig();
        c.mountainHeightScale = s.mountainHeightScale();
        c.continentalScale = s.continentalScale();
        c.erosionStrength = s.erosionStrength();
        c.seaLevel = s.seaLevel();
        c.caveDensity = s.caveDensity();
        c.caveFrequency = s.caveFrequency();
        c.enableMegaCaverns = s.enableMegaCaverns();
        c.enableUndergroundRivers = s.enableUndergroundRivers();
        c.riverWidth = s.riverWidth();
        c.riverDepth = s.riverDepth();
        c.snowLineBase = b.snowLineBase();
        c.biomeBlendWidth = b.biomeBlendWidth();
        c.climateBlendWidth = b.climateBlendWidth();
        c.deepOceanOffset = b.deepOceanOffset();
        c.coastalOffset = b.coastalOffset();
        c.lowlandOffset = b.lowlandOffset();
        c.cavernCenterY = b.cavernCenterY();
        c.enableCastles = b.enableCastles();
        c.enableRuins = b.enableRuins();
        c.enableDungeons = b.enableDungeons();
        c.enableWatchtowers = b.enableWatchtowers();
        c.validate();  // also re-pins worldMinY/worldHeight to the dimension type
        return from(c);
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
