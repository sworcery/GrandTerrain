package com.grandterrain.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game configuration screen built with Cloth Config API.
 * Players can adjust world generation parameters without editing files.
 */
public class ConfigScreen {

    public static Screen create(Screen parent) {
        GrandterrainConfig config = ConfigManager.getConfig();
        GrandterrainConfig defaults = new GrandterrainConfig();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("GrandTerrain Settings"));

        builder.setSavingRunnable(() -> ConfigManager.save(config));

        ConfigEntryBuilder entries = builder.entryBuilder();

        // Terrain category
        ConfigCategory terrain = builder.getOrCreateCategory(Component.literal("Terrain"));

        terrain.addEntry(entries.startFloatField(
                        Component.literal("Mountain Height Scale"), config.mountainHeightScale)
                .setDefaultValue(defaults.mountainHeightScale)
                .setMin(1.0f).setMax(10.0f)
                .setTooltip(Component.literal("How tall mountains are relative to vanilla. 5.0 = 5x vanilla height."))
                .setSaveConsumer(v -> config.mountainHeightScale = v)
                .build());

        terrain.addEntry(entries.startFloatField(
                        Component.literal("Continental Scale"), config.continentalScale)
                .setDefaultValue(defaults.continentalScale)
                .setMin(0.5f).setMax(3.0f)
                .setTooltip(Component.literal("Scale of continental landmasses. Higher = larger continents."))
                .setSaveConsumer(v -> config.continentalScale = v)
                .build());

        terrain.addEntry(entries.startFloatField(
                        Component.literal("Erosion Strength"), config.erosionStrength)
                .setDefaultValue(defaults.erosionStrength)
                .setMin(0.0f).setMax(3.0f)
                .setTooltip(Component.literal("How much erosion carves valleys. 0 = no erosion."))
                .setSaveConsumer(v -> config.erosionStrength = v)
                .build());

        terrain.addEntry(entries.startIntField(
                        Component.literal("Sea Level"), config.seaLevel)
                .setDefaultValue(defaults.seaLevel)
                .setMin(60).setMax(200)
                .setTooltip(Component.literal("Height of the ocean surface."))
                .setSaveConsumer(v -> config.seaLevel = v)
                .build());

        // World height is fixed by the grandterrain dimension type JSON (-256..768);
        // it cannot be changed at runtime, so it is not exposed here.

        // Caves category
        ConfigCategory caves = builder.getOrCreateCategory(Component.literal("Caves"));

        caves.addEntry(entries.startFloatField(
                        Component.literal("Cave Density"), config.caveDensity)
                .setDefaultValue(defaults.caveDensity)
                .setMin(0.0f).setMax(3.0f)
                .setTooltip(Component.literal("Overall density of cave generation."))
                .setSaveConsumer(v -> config.caveDensity = v)
                .build());

        caves.addEntry(entries.startFloatField(
                        Component.literal("Cave Frequency"), config.caveFrequency)
                .setDefaultValue(defaults.caveFrequency)
                .setMin(0.5f).setMax(3.0f)
                .setTooltip(Component.literal("How frequently cave tunnels appear."))
                .setSaveConsumer(v -> config.caveFrequency = v)
                .build());

        caves.addEntry(entries.startBooleanToggle(
                        Component.literal("Enable Mega Caverns"), config.enableMegaCaverns)
                .setDefaultValue(defaults.enableMegaCaverns)
                .setTooltip(Component.literal("Generate huge underground caverns."))
                .setSaveConsumer(v -> config.enableMegaCaverns = v)
                .build());

        caves.addEntry(entries.startBooleanToggle(
                        Component.literal("Enable Underground Rivers"), config.enableUndergroundRivers)
                .setDefaultValue(defaults.enableUndergroundRivers)
                .setTooltip(Component.literal("Generate flowing rivers underground."))
                .setSaveConsumer(v -> config.enableUndergroundRivers = v)
                .build());

        caves.addEntry(entries.startIntField(
                        Component.literal("Cavern Center Y"), config.cavernCenterY)
                .setDefaultValue(defaults.cavernCenterY)
                .setMin(-300).setMax(0)
                .setTooltip(Component.literal("Y level where mega caverns center. Lower = deeper caverns."))
                .setSaveConsumer(v -> config.cavernCenterY = v)
                .build());

        // Rivers category
        ConfigCategory rivers = builder.getOrCreateCategory(Component.literal("Rivers"));

        rivers.addEntry(entries.startFloatField(
                        Component.literal("River Width"), config.riverWidth)
                .setDefaultValue(defaults.riverWidth)
                .setMin(0.5f).setMax(3.0f)
                .setTooltip(Component.literal("How wide surface rivers are."))
                .setSaveConsumer(v -> config.riverWidth = v)
                .build());

        rivers.addEntry(entries.startFloatField(
                        Component.literal("River Depth"), config.riverDepth)
                .setDefaultValue(defaults.riverDepth)
                .setMin(0.5f).setMax(3.0f)
                .setTooltip(Component.literal("How deep rivers carve into terrain."))
                .setSaveConsumer(v -> config.riverDepth = v)
                .build());

        // Snow category
        ConfigCategory snow = builder.getOrCreateCategory(Component.literal("Snow & Climate"));

        snow.addEntry(entries.startIntField(
                        Component.literal("Snow Line Base"), config.snowLineBase)
                .setDefaultValue(defaults.snowLineBase)
                .setMin(200).setMax(600)
                .setTooltip(Component.literal("Altitude where snow starts."))
                .setSaveConsumer(v -> config.snowLineBase = v)
                .build());

        snow.addEntry(entries.startIntField(
                        Component.literal("Biome Blend Width"), config.biomeBlendWidth)
                .setDefaultValue(defaults.biomeBlendWidth)
                .setMin(0).setMax(12)
                .setTooltip(Component.literal("Altitude jitter in blocks at biome boundaries. 0 = sharp edges."))
                .setSaveConsumer(v -> config.biomeBlendWidth = v)
                .build());

        snow.addEntry(entries.startFloatField(
                        Component.literal("Climate Blend Width"), config.climateBlendWidth)
                .setDefaultValue(defaults.climateBlendWidth)
                .setMin(0.0f).setMax(0.2f)
                .setTooltip(Component.literal("Noise jitter at climate zone boundaries. Higher = smoother transitions."))
                .setSaveConsumer(v -> config.climateBlendWidth = v)
                .build());

        // Biome thresholds category
        ConfigCategory biomes = builder.getOrCreateCategory(Component.literal("Biome Thresholds"));

        biomes.addEntry(entries.startIntField(
                        Component.literal("Deep Ocean Offset"), config.deepOceanOffset)
                .setDefaultValue(defaults.deepOceanOffset)
                .setMin(-200).setMax(-10)
                .setTooltip(Component.literal("Y offset from sea level where deep ocean begins. More negative = deeper ocean floor."))
                .setSaveConsumer(v -> config.deepOceanOffset = v)
                .build());

        biomes.addEntry(entries.startIntField(
                        Component.literal("Coastal Offset"), config.coastalOffset)
                .setDefaultValue(defaults.coastalOffset)
                .setMin(-100).setMax(0)
                .setTooltip(Component.literal("Y offset from sea level where coastal zone begins."))
                .setSaveConsumer(v -> config.coastalOffset = v)
                .build());

        biomes.addEntry(entries.startIntField(
                        Component.literal("Lowland Offset"), config.lowlandOffset)
                .setDefaultValue(defaults.lowlandOffset)
                .setMin(0).setMax(100)
                .setTooltip(Component.literal("Y offset above sea level where lowland biomes begin."))
                .setSaveConsumer(v -> config.lowlandOffset = v)
                .build());

        // Structures category
        ConfigCategory structures = builder.getOrCreateCategory(Component.literal("Structures"));

        structures.addEntry(entries.startBooleanToggle(
                        Component.literal("Enable Castles"), config.enableCastles)
                .setDefaultValue(defaults.enableCastles)
                .setSaveConsumer(v -> config.enableCastles = v)
                .build());

        structures.addEntry(entries.startBooleanToggle(
                        Component.literal("Enable Ruins"), config.enableRuins)
                .setDefaultValue(defaults.enableRuins)
                .setSaveConsumer(v -> config.enableRuins = v)
                .build());

        structures.addEntry(entries.startBooleanToggle(
                        Component.literal("Enable Dungeons"), config.enableDungeons)
                .setDefaultValue(defaults.enableDungeons)
                .setSaveConsumer(v -> config.enableDungeons = v)
                .build());

        structures.addEntry(entries.startBooleanToggle(
                        Component.literal("Enable Watchtowers"), config.enableWatchtowers)
                .setDefaultValue(defaults.enableWatchtowers)
                .setSaveConsumer(v -> config.enableWatchtowers = v)
                .build());

        return builder.build();
    }
}
