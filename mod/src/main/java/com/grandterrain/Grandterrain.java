package com.grandterrain;

import com.grandterrain.config.ConfigManager;
import com.grandterrain.worldgen.GrandterrainChunkGenerator;
import com.grandterrain.worldgen.biome.GrandterrainBiomeSource;
import com.grandterrain.worldgen.biome.GrandterrainBiomes;
import com.grandterrain.worldgen.structure.GrandterrainStructurePieces;
import com.grandterrain.worldgen.structure.GrandterrainStructures;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Grandterrain implements ModInitializer {

    public static final String MOD_ID = "grandterrain";
    public static final Logger LOGGER = LoggerFactory.getLogger("GrandTerrain");

    @Override
    public void onInitialize() {
        LOGGER.info("GrandTerrain initializing - massive world generation awaits!");

        ConfigManager.getConfig();

        Registry.register(
                BuiltInRegistries.CHUNK_GENERATOR,
                Identifier.fromNamespaceAndPath(MOD_ID, "grandterrain"),
                GrandterrainChunkGenerator.CODEC
        );

        Registry.register(
                BuiltInRegistries.BIOME_SOURCE,
                Identifier.fromNamespaceAndPath(MOD_ID, "grandterrain"),
                GrandterrainBiomeSource.CODEC
        );

        GrandterrainBiomes.init();
        GrandterrainStructurePieces.register();
        GrandterrainStructures.register();

        LOGGER.info("GrandTerrain initialized successfully.");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
