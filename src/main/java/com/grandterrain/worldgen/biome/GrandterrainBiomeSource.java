package com.grandterrain.worldgen.biome;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

/**
 * Altitude and climate-based biome source for GrandTerrain.
 * Uses continentalness, altitude, and humidity noise to select biomes.
 */
public class GrandterrainBiomeSource extends BiomeSource {

    public static final MapCodec<GrandterrainBiomeSource> CODEC = MapCodec.unit(
            () -> { throw new IllegalStateException("GrandterrainBiomeSource requires a HolderGetter"); }
    );

    private final Holder<Biome> deepOcean;
    private final Holder<Biome> coastalCliffs;
    private final Holder<Biome> lowlandPlains;
    private final Holder<Biome> temperateForest;
    private final Holder<Biome> alpineMeadow;
    private final Holder<Biome> mountainPineForest;
    private final Holder<Biome> rockyHighlands;
    private final Holder<Biome> snowPeaks;
    private final Holder<Biome> deepValley;
    private final Holder<Biome> volcanicRegion;
    // Vanilla fallbacks for cave biomes
    private final Holder<Biome> lushCaves;
    private final Holder<Biome> deepDark;

    public GrandterrainBiomeSource(HolderGetter<Biome> biomeGetter) {
        // Custom biomes (may fall back to vanilla if not yet registered)
        this.deepOcean = getOrFallback(biomeGetter, GrandterrainBiomes.DEEP_OCEAN, Biomes.DEEP_OCEAN);
        this.coastalCliffs = getOrFallback(biomeGetter, GrandterrainBiomes.COASTAL_CLIFFS, Biomes.STONY_SHORE);
        this.lowlandPlains = getOrFallback(biomeGetter, GrandterrainBiomes.LOWLAND_PLAINS, Biomes.PLAINS);
        this.temperateForest = getOrFallback(biomeGetter, GrandterrainBiomes.TEMPERATE_FOREST, Biomes.FOREST);
        this.alpineMeadow = getOrFallback(biomeGetter, GrandterrainBiomes.ALPINE_MEADOW, Biomes.MEADOW);
        this.mountainPineForest = getOrFallback(biomeGetter, GrandterrainBiomes.MOUNTAIN_PINE_FOREST, Biomes.OLD_GROWTH_SPRUCE_TAIGA);
        this.rockyHighlands = getOrFallback(biomeGetter, GrandterrainBiomes.ROCKY_HIGHLANDS, Biomes.STONY_PEAKS);
        this.snowPeaks = getOrFallback(biomeGetter, GrandterrainBiomes.SNOW_PEAKS, Biomes.FROZEN_PEAKS);
        this.deepValley = getOrFallback(biomeGetter, GrandterrainBiomes.DEEP_VALLEY, Biomes.DARK_FOREST);
        this.volcanicRegion = getOrFallback(biomeGetter, GrandterrainBiomes.VOLCANIC_REGION, Biomes.BASALT_DELTAS);
        this.lushCaves = biomeGetter.getOrThrow(Biomes.LUSH_CAVES);
        this.deepDark = biomeGetter.getOrThrow(Biomes.DEEP_DARK);
    }

    private static Holder<Biome> getOrFallback(HolderGetter<Biome> getter,
                                                net.minecraft.resources.ResourceKey<Biome> primary,
                                                net.minecraft.resources.ResourceKey<Biome> fallback) {
        try {
            return getter.getOrThrow(primary);
        } catch (Exception e) {
            return getter.getOrThrow(fallback);
        }
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(
                deepOcean, coastalCliffs, lowlandPlains, temperateForest,
                alpineMeadow, mountainPineForest, rockyHighlands, snowPeaks,
                deepValley, volcanicRegion, lushCaves, deepDark
        );
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // Biome coords are world coords / 4
        int blockY = y * 4;

        // Underground biomes
        if (blockY < -50) {
            if (blockY < -150) {
                return deepDark;
            }
            return lushCaves;
        }

        // Use the sampler's climate parameters if available
        // The climate parameters are set by the noise router
        Climate.TargetPoint target = sampler.sample(x, y, z);

        // Extract continentalness and erosion from climate
        // These are in "quart" units (divided by 10000)
        float continentalness = Climate.unquantizeCoord(target.continentalness());
        float erosion = Climate.unquantizeCoord(target.erosion());
        float temperature = Climate.unquantizeCoord(target.temperature());
        float humidity = Climate.unquantizeCoord(target.humidity());

        // Simple altitude-based selection as primary (since we control the terrain)
        if (blockY < 60) {
            return deepOcean;
        }

        if (blockY < 135) {
            // Near sea level: coast or lowland
            if (blockY < 100) {
                return coastalCliffs;
            }
            return lowlandPlains;
        }

        if (blockY < 200) {
            // Low-mid altitude
            if (humidity > 0.3f) {
                return temperateForest;
            }
            return lowlandPlains;
        }

        if (blockY < 280) {
            // Mid altitude
            if (humidity > 0.4f) {
                return mountainPineForest;
            }
            return alpineMeadow;
        }

        if (blockY < 380) {
            // High altitude
            if (erosion > 0.5f) {
                return deepValley;
            }
            return rockyHighlands;
        }

        // Very high: snow peaks
        return snowPeaks;
    }
}
