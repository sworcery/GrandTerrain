package com.grandterrain.worldgen.biome;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

public class GrandterrainBiomes {

    public static final ResourceKey<Biome> DEEP_OCEAN = register("deep_ocean");
    public static final ResourceKey<Biome> COASTAL_CLIFFS = register("coastal_cliffs");
    public static final ResourceKey<Biome> LOWLAND_PLAINS = register("lowland_plains");
    public static final ResourceKey<Biome> TEMPERATE_FOREST = register("temperate_forest");
    public static final ResourceKey<Biome> ALPINE_MEADOW = register("alpine_meadow");
    public static final ResourceKey<Biome> MOUNTAIN_PINE_FOREST = register("mountain_pine_forest");
    public static final ResourceKey<Biome> ROCKY_HIGHLANDS = register("rocky_highlands");
    public static final ResourceKey<Biome> SNOW_PEAKS = register("snow_peaks");
    public static final ResourceKey<Biome> DEEP_VALLEY = register("deep_valley");
    public static final ResourceKey<Biome> VOLCANIC_REGION = register("volcanic_region");

    private static ResourceKey<Biome> register(String name) {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath("grandterrain", name));
    }

    public static void init() {
        // Force class loading to register all keys
    }
}
