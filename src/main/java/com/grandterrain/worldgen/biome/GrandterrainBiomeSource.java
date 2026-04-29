package com.grandterrain.worldgen.biome;

import com.grandterrain.Grandterrain;
import com.grandterrain.config.ConfigManager;
import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.noise.ClimateNoise;
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

public class GrandterrainBiomeSource extends BiomeSource {

    public static final MapCodec<GrandterrainBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    RegistryOps.retrieveGetter(Registries.BIOME)
            ).apply(instance, GrandterrainBiomeSource::new)
    );

    private final HolderGetter<Biome> biomeGetter;
    private final ConfigSnapshot config;

    // Altitude thresholds (world-Y)
    private final int yDeepOcean;
    private final int yCoastalCliffs;
    private final int yLowlandPlains;
    private final int yTemperateForest;
    private final int yAlpineMeadow;
    private final int yMountainPine;
    private final int yRockyHighlands;

    // Cave boundaries
    private final int yLushCaveCeiling;
    private final int yDeepDarkCeiling;

    // Original 10 biomes
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

    // 6 new climate biomes
    private final Holder<Biome> desert;
    private final Holder<Biome> savanna;
    private final Holder<Biome> swamp;
    private final Holder<Biome> tundra;
    private final Holder<Biome> birchForest;
    private final Holder<Biome> darkForest;

    // Cave biomes (vanilla)
    private final Holder<Biome> lushCaves;
    private final Holder<Biome> deepDark;

    private volatile ClimateNoise climateNoise;

    public GrandterrainBiomeSource(HolderGetter<Biome> biomeGetter) {
        this(biomeGetter, ConfigSnapshot.from(ConfigManager.getConfig()));
    }

    public GrandterrainBiomeSource(HolderGetter<Biome> biomeGetter, ConfigSnapshot config) {
        this.biomeGetter = biomeGetter;
        this.config = config;

        int seaLevel = config.seaLevel();
        int snowLine = config.snowLineBase();
        int surfaceToSnowRange = snowLine - seaLevel;

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
        this.deepValley = getOrFallback(biomeGetter, GrandterrainBiomes.DEEP_VALLEY, Biomes.FOREST);
        this.volcanicRegion = getOrFallback(biomeGetter, GrandterrainBiomes.VOLCANIC_REGION, Biomes.BADLANDS);

        this.desert = getOrFallback(biomeGetter, GrandterrainBiomes.DESERT, Biomes.DESERT);
        this.savanna = getOrFallback(biomeGetter, GrandterrainBiomes.SAVANNA, Biomes.SAVANNA);
        this.swamp = getOrFallback(biomeGetter, GrandterrainBiomes.SWAMP, Biomes.SWAMP);
        this.tundra = getOrFallback(biomeGetter, GrandterrainBiomes.TUNDRA, Biomes.SNOWY_TAIGA);
        this.birchForest = getOrFallback(biomeGetter, GrandterrainBiomes.BIRCH_FOREST, Biomes.BIRCH_FOREST);
        this.darkForest = getOrFallback(biomeGetter, GrandterrainBiomes.DARK_FOREST, Biomes.DARK_FOREST);

        this.lushCaves = biomeGetter.getOrThrow(Biomes.LUSH_CAVES);
        this.deepDark = biomeGetter.getOrThrow(Biomes.DEEP_DARK);
    }

    public void initClimate(long seed) {
        this.climateNoise = new ClimateNoise(seed);
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
                deepValley, volcanicRegion,
                desert, savanna, swamp, tundra, birchForest, darkForest,
                lushCaves, deepDark
        );
    }

    private static final int BLEND_Y = 4;
    private static final double BLEND_CLIMATE = 0.05;

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int blockY = y * 4;
        int blockX = x * 4;
        int blockZ = z * 4;

        if (blockY < yDeepDarkCeiling) return deepDark;
        if (blockY < yLushCaveCeiling) return lushCaves;

        if (blockY < yDeepOcean) return deepOcean;
        if (blockY < yCoastalCliffs) return coastalCliffs;

        int jy = blockY + altitudeJitter(blockX, blockZ);

        if (jy >= yRockyHighlands) return snowPeaks;

        ClimateNoise cn = climateNoise;
        if (cn == null) {
            return selectByAltitudeOnly(jy);
        }

        double temp = cn.temperature(blockX, blockZ)
                + climateJitter(blockX, blockZ, 0);
        double humid = cn.humidity(blockX, blockZ)
                + climateJitter(blockX, blockZ, 1);

        if (jy >= yMountainPine) {
            if (temp < -0.3) return tundra;
            return rockyHighlands;
        }

        if (jy >= yAlpineMeadow) {
            if (temp < -0.3) return tundra;
            return mountainPineForest;
        }

        if (jy >= yTemperateForest) {
            if (temp < -0.3) return tundra;
            if (humid > 0.3) return deepValley;
            return alpineMeadow;
        }

        return selectLowMidBiome(temp, humid);
    }

    private int altitudeJitter(int blockX, int blockZ) {
        int bx = blockX >> 2;
        int bz = blockZ >> 2;
        int hash = ((bx * 73856093) ^ (bz * 83492791)) & 0xFF;
        return (hash * (2 * BLEND_Y + 1)) / 256 - BLEND_Y;
    }

    private double climateJitter(int blockX, int blockZ, int axis) {
        int bx = blockX >> 2;
        int bz = blockZ >> 2;
        int hash = ((bx * 19349669 + axis * 73856093) ^ (bz * 83492791)) & 0xFF;
        return ((double) hash / 256.0 - 0.5) * 2.0 * BLEND_CLIMATE;
    }

    private Holder<Biome> selectLowMidBiome(double temp, double humid) {
        if (temp > 0.5) {
            if (humid < -0.4) return volcanicRegion;
            if (humid < -0.2) return desert;
            if (humid > 0.4) return swamp;
            return savanna;
        }

        if (temp < -0.3) {
            return tundra;
        }

        if (temp > 0.15) {
            if (humid > 0.4) return darkForest;
            if (humid > 0.0) return temperateForest;
            return lowlandPlains;
        }

        if (humid > 0.2) return birchForest;
        return lowlandPlains;
    }

    private Holder<Biome> selectByAltitudeOnly(int blockY) {
        if (blockY < yLowlandPlains) return lowlandPlains;
        if (blockY < yTemperateForest) return temperateForest;
        if (blockY < yAlpineMeadow) return alpineMeadow;
        if (blockY < yMountainPine) return mountainPineForest;
        return rockyHighlands;
    }
}
