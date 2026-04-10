package com.grandterrain.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("GrandTerrain");
    private static final String CONFIG_FILE = "grandterrain.properties";
    private static GrandterrainConfig instance;

    public static synchronized GrandterrainConfig getConfig() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static GrandterrainConfig load() {
        GrandterrainConfig config = new GrandterrainConfig();
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);

        if (Files.exists(configPath)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(configPath)) {
                props.load(in);
                readProperties(config, props);
            } catch (IOException e) {
                LOGGER.error("Failed to load config, using defaults", e);
            }
        } else {
            save(config);
        }

        config.validate();
        return config;
    }

    public static void save(GrandterrainConfig config) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE);
        Properties props = new Properties();
        writeProperties(config, props);

        try (OutputStream out = Files.newOutputStream(configPath)) {
            props.store(out, "GrandTerrain World Generation Config");
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    private static void readProperties(GrandterrainConfig config, Properties props) {
        config.mountainHeightScale = getFloat(props, "terrain.mountainHeightScale", config.mountainHeightScale);
        config.continentalScale = getFloat(props, "terrain.continentalScale", config.continentalScale);
        config.erosionStrength = getFloat(props, "terrain.erosionStrength", config.erosionStrength);
        config.seaLevel = getInt(props, "terrain.seaLevel", config.seaLevel);
        config.worldMinY = getInt(props, "terrain.worldMinY", config.worldMinY);
        config.worldHeight = getInt(props, "terrain.worldHeight", config.worldHeight);

        config.caveDensity = getFloat(props, "caves.density", config.caveDensity);
        config.caveFrequency = getFloat(props, "caves.frequency", config.caveFrequency);
        config.enableMegaCaverns = getBool(props, "caves.enableMegaCaverns", config.enableMegaCaverns);
        config.enableUndergroundRivers = getBool(props, "caves.enableUndergroundRivers", config.enableUndergroundRivers);

        config.riverWidth = getFloat(props, "rivers.width", config.riverWidth);
        config.riverDepth = getFloat(props, "rivers.depth", config.riverDepth);

        config.snowLineBase = getInt(props, "snow.lineBase", config.snowLineBase);
        config.snowLineVariation = getFloat(props, "snow.lineVariation", config.snowLineVariation);

        config.structureDensity = getFloat(props, "structures.density", config.structureDensity);
        config.enableCastles = getBool(props, "structures.enableCastles", config.enableCastles);
        config.enableRuins = getBool(props, "structures.enableRuins", config.enableRuins);
        config.enableDungeons = getBool(props, "structures.enableDungeons", config.enableDungeons);

        config.enableVolcanicRegions = getBool(props, "biomes.enableVolcanicRegions", config.enableVolcanicRegions);
    }

    private static void writeProperties(GrandterrainConfig config, Properties props) {
        props.setProperty("terrain.mountainHeightScale", String.valueOf(config.mountainHeightScale));
        props.setProperty("terrain.continentalScale", String.valueOf(config.continentalScale));
        props.setProperty("terrain.erosionStrength", String.valueOf(config.erosionStrength));
        props.setProperty("terrain.seaLevel", String.valueOf(config.seaLevel));
        props.setProperty("terrain.worldMinY", String.valueOf(config.worldMinY));
        props.setProperty("terrain.worldHeight", String.valueOf(config.worldHeight));

        props.setProperty("caves.density", String.valueOf(config.caveDensity));
        props.setProperty("caves.frequency", String.valueOf(config.caveFrequency));
        props.setProperty("caves.enableMegaCaverns", String.valueOf(config.enableMegaCaverns));
        props.setProperty("caves.enableUndergroundRivers", String.valueOf(config.enableUndergroundRivers));

        props.setProperty("rivers.width", String.valueOf(config.riverWidth));
        props.setProperty("rivers.depth", String.valueOf(config.riverDepth));

        props.setProperty("snow.lineBase", String.valueOf(config.snowLineBase));
        props.setProperty("snow.lineVariation", String.valueOf(config.snowLineVariation));

        props.setProperty("structures.density", String.valueOf(config.structureDensity));
        props.setProperty("structures.enableCastles", String.valueOf(config.enableCastles));
        props.setProperty("structures.enableRuins", String.valueOf(config.enableRuins));
        props.setProperty("structures.enableDungeons", String.valueOf(config.enableDungeons));

        props.setProperty("biomes.enableVolcanicRegions", String.valueOf(config.enableVolcanicRegions));
    }

    private static float getFloat(Properties props, String key, float def) {
        String val = props.getProperty(key);
        if (val == null) return def;
        try {
            return Float.parseFloat(val);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int getInt(Properties props, String key, int def) {
        String val = props.getProperty(key);
        if (val == null) return def;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean getBool(Properties props, String key, boolean def) {
        String val = props.getProperty(key);
        if (val == null) return def;
        return Boolean.parseBoolean(val);
    }
}
