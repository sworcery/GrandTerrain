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

        terrain.addEntry(entries.startIntField(
                        Component.literal("World Height"), config.worldHeight)
                .setDefaultValue(defaults.worldHeight)
                .setMin(512).setMax(2048)
                .setTooltip(Component.literal("Total world height in blocks."))
                .setSaveConsumer(v -> config.worldHeight = v)
                .build());

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
