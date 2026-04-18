package com.grandterrain.worldgen.biome;

import com.grandterrain.Grandterrain;
import com.grandterrain.config.ConfigManager;
import com.grandterrain.config.ConfigSnapshot;
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
 * Altitude-based biome source for GrandTerrain. Thresholds scale with configured
 * sea level and snow line so the biome placement remains correct when the user
 * changes terrain altitude.
 */
public class GrandterrainBiomeSource extends BiomeSource {

    public static final MapCodec<GrandterrainBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    RegistryOps.retrieveGetter(Registries.BIOME)
            ).apply(instance, GrandterrainBiomeSource::new)
    );

    private final HolderGetter<Biome> biomeGetter;
    private final ConfigSnapshot config;

    // Precomputed altitude thresholds (world-Y, not biome-Y)
    private final int yDeepOcean;        // below: deep ocean
    private final int yCoastalCliffs;    // below: coastal cliffs
    private final int yLowlandPlains;    // below: lowland plains
    private final int yTemperateForest;  // below: temperate forest
    private final int yAlpineMeadow;     // below: alpine meadow
    private final int yMountainPine;     // below: mountain pine
    private final int yRockyHighlands;   // below: rocky highlands
    // at or above yRockyHighlands → snow peaks

    // Cave boundaries
    private final int yLushCaveCeiling;  // above: surface biomes; below: cave biomes
    private final int yDeepDarkCeiling;  // below yLushCave, above this: lush; below: deep dark

    private final Holder<Biome> deepOcean;
    private final Holder<Biome> coastalCliffs;
    private final Holder<Biome> lowlandPlains;
    private final Holder<Biome> temperateForest;
    private final Holder<Biome> alpineMeadow;
    private final Holder<Biome> mountainPineForest;
    private final Holder<Biome> rockyHighlands;
    private final Holder<Biome> snowPeaks;
    private final Holder<Biome> lushCaves;
    private final Holder<Biome> deepDark;

    public GrandterrainBiomeSource(HolderGetter<Biome> biomeGetter) {
        this(biomeGetter, ConfigSnapshot.from(ConfigManager.getConfig()));
    }

    public GrandterrainBiomeSource(HolderGetter<Biome> biomeGetter, ConfigSnapshot config) {
        this.biomeGetter = biomeGetter;
        this.config = config;

        int seaLevel = config.seaLevel();
        int snowLine = config.snowLineBase();
        int surfaceToSnowRange = snowLine - seaLevel;

        // Surface tier thresholds scale with seaLevel → snowLine band.
        // Tiers are clamped monotonically so tight surfaceToSnowRange values
        // can't invert the chain (e.g. mountainPine ending up above rockyHighlands).
        int t0 = seaLevel - 60;
        int t1 = Math.max(t0 + 5, seaLevel - 20);
        int t2 = Math.max(t1 + 5, seaLevel + 10);
        int t3 = Math.max(t2 + 5, seaLevel + Math.max(20, surfaceToSnowRange / 4));
        int t4 = Math.max(t3 + 5, seaLevel + Math.max(60, surfaceToSnowRange / 2));
        int t5 = Math.max(t4 + 5, seaLevel + Math.max(100, (surfaceToSnowRange * 3) / 4));
        int t6 = Math.max(t5 + 5, snowLine - 20);
        this.yDeepOcean = t0;
        this.yCoastalCliffs = t1;
        this.yLowlandPlains = t2;
        this.yTemperateForest = t3;
        this.yAlpineMeadow = t4;
        this.yMountainPine = t5;
        this.yRockyHighlands = t6;

        // Cave tiers, clamped to stay above worldMinY so very small worlds stay valid.
        int worldMinY = config.worldMinY();
        this.yLushCaveCeiling = Math.max(worldMinY + 100, seaLevel - 80);
        this.yDeepDarkCeiling = Math.max(worldMinY + 8, yLushCaveCeiling - 100);

        this.deepOcean = getOrFallback(biomeGetter, GrandterrainBiomes.DEEP_OCEAN, Biomes.DEEP_OCEAN);
        this.coastalCliffs = getOrFallback(biomeGetter, GrandterrainBiomes.COASTAL_CLIFFS, Biomes.STONY_SHORE);
        this.lowlandPlains = getOrFallback(biomeGetter, GrandterrainBiomes.LOWLAND_PLAINS, Biomes.PLAINS);
        this.temperateForest = getOrFallback(biomeGetter, GrandterrainBiomes.TEMPERATE_FOREST, Biomes.FOREST);
        this.alpineMeadow = getOrFallback(biomeGetter, GrandterrainBiomes.ALPINE_MEADOW, Biomes.MEADOW);
        this.mountainPineForest = getOrFallback(biomeGetter, GrandterrainBiomes.MOUNTAIN_PINE_FOREST, Biomes.OLD_GROWTH_SPRUCE_TAIGA);
        this.rockyHighlands = getOrFallback(biomeGetter, GrandterrainBiomes.ROCKY_HIGHLANDS, Biomes.STONY_PEAKS);
        this.snowPeaks = getOrFallback(biomeGetter, GrandterrainBiomes.SNOW_PEAKS, Biomes.FROZEN_PEAKS);
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
                lushCaves, deepDark
        );
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int blockY = y * 4; // biome coords → world coords

        if (blockY < yDeepDarkCeiling) return deepDark;
        if (blockY < yLushCaveCeiling) return lushCaves;

        if (blockY < yDeepOcean) return deepOcean;
        if (blockY < yCoastalCliffs) return coastalCliffs;
        if (blockY < yLowlandPlains) return lowlandPlains;
        if (blockY < yTemperateForest) return temperateForest;
        if (blockY < yAlpineMeadow) return alpineMeadow;
        if (blockY < yMountainPine) return mountainPineForest;
        if (blockY < yRockyHighlands) return rockyHighlands;
        return snowPeaks;
    }
}
