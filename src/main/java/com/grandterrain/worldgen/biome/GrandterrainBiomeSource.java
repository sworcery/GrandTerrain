package com.grandterrain.worldgen.biome;

import com.grandterrain.Grandterrain;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

/**
 * Altitude-based biome source for GrandTerrain. Uses a per-chunk-generator
 * injected {@link HolderGetter} (via RegistryOps context) to look up biomes.
 *
 * The Climate.Sampler passed to getNoiseBiome is vanilla-empty for this generator,
 * so biome selection is driven by altitude + stored climate noise, not the sampler.
 */
public class GrandterrainBiomeSource extends BiomeSource {

    public static final MapCodec<GrandterrainBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    RegistryOps.retrieveGetter(Registries.BIOME)
            ).apply(instance, GrandterrainBiomeSource::new)
    );

    private final HolderGetter<Biome> biomeGetter;
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
    private final Holder<Biome> lushCaves;
    private final Holder<Biome> deepDark;

    public GrandterrainBiomeSource(HolderGetter<Biome> biomeGetter) {
        this.biomeGetter = biomeGetter;
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

    public HolderGetter<Biome> getBiomeGetter() {
        return biomeGetter;
    }

    private static Holder<Biome> getOrFallback(HolderGetter<Biome> getter,
                                                ResourceKey<Biome> primary,
                                                ResourceKey<Biome> fallback) {
        return getter.get(primary).orElseGet(() -> {
            Grandterrain.LOGGER.warn("Biome {} not found; falling back to {}",
                    primary.identifier(), fallback.identifier());
            return getter.getOrThrow(fallback);
        });
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
        int blockY = y * 4;

        // Underground biomes
        if (blockY < -50) {
            return blockY < -150 ? deepDark : lushCaves;
        }

        // Altitude-driven tiers (humidity/climate branches removed - sampler is empty
        // for this generator; using it would produce dead branches).
        if (blockY < 60)  return deepOcean;
        if (blockY < 100) return coastalCliffs;
        if (blockY < 135) return lowlandPlains;
        if (blockY < 200) return temperateForest;
        if (blockY < 280) return alpineMeadow;
        if (blockY < 350) return mountainPineForest;
        if (blockY < 420) return rockyHighlands;
        return snowPeaks;
    }
}
