package com.grandterrain.worldgen.biome;

import com.grandterrain.Grandterrain;
import com.grandterrain.config.ConfigManager;
import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.noise.ClimateNoise;
import com.grandterrain.worldgen.noise.TerrainDensityFunction;
import com.grandterrain.worldgen.noise.WorldgenHash;
import com.mojang.datafixers.util.Pair;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class GrandterrainBiomeSource extends BiomeSource {

    /**
     * Carries its own copy of the settings (optional, like the generator's)
     * because Minecraft decodes the biome source independently of the chunk
     * generator — it cannot be handed the generator's snapshot at load time.
     * Both are populated from the same local config at world creation and
     * both read the same persisted values back afterwards, so they agree.
     */
    public static final MapCodec<GrandterrainBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    RegistryOps.retrieveGetter(Registries.BIOME),
                    ConfigSnapshot.CODEC.optionalFieldOf("settings").forGetter(src -> java.util.Optional.of(src.config))
            ).apply(instance, GrandterrainBiomeSource::new)
    );

    private final HolderGetter<Biome> biomeGetter;
    private final ConfigSnapshot config;

    private final int yDeepOcean;
    private final int yCoastalCliffs;

    private final Holder<Biome> deepOcean;
    private final Holder<Biome> caverns;
    private final Holder<Biome> coastalCliffs;
    private final Holder<Biome> lowlandPlains;
    private final Holder<Biome> temperateForest;
    private final Holder<Biome> alpineMeadow;
    private final Holder<Biome> mountainPineForest;
    private final Holder<Biome> rockyHighlands;
    private final Holder<Biome> snowPeaks;
    private final Holder<Biome> deepValley;
    private final Holder<Biome> volcanicRegion;
    private final Holder<Biome> desert;
    private final Holder<Biome> savanna;
    private final Holder<Biome> swamp;
    private final Holder<Biome> tundra;
    private final Holder<Biome> birchForest;
    private final Holder<Biome> darkForest;

    private final int blendY;
    private final double blendClimate;

    private static final Climate.Parameter PARAM_FULL = Climate.Parameter.span(-1.5f, 1.5f);

    private final Climate.ParameterList<Holder<Biome>> biomeParameters;
    private final float altParamScale;
    private final float altParamOffset;

    private volatile ClimateNoise climateNoise;
    /** Set with the climate noise in createState; null only before that. */
    private volatile TerrainDensityFunction terrain;

    /**
     * 16-entry direct-mapped per-thread cache of surface height, one slot per
     * quart column of a chunk (4x4). MC fills biomes with z innermost, so a
     * single-entry cache would miss on literally every call; indexing by the
     * low bits of the quart coords makes every column a permanent hit after
     * its first touch (~16 surface evaluations per chunk instead of ~4096).
     */
    private static final class ColumnCache {
        final int[] keyX = new int[16];
        final int[] keyZ = new int[16];
        final double[] surface = new double[16];
        final boolean[] valid = new boolean[16];
    }
    private final ThreadLocal<ColumnCache> columnCache = ThreadLocal.withInitial(ColumnCache::new);

    private double surfaceAt(TerrainDensityFunction t, int quartX, int quartZ, int blockX, int blockZ) {
        ColumnCache c = columnCache.get();
        int i = ((quartX & 3) << 2) | (quartZ & 3);
        if (!c.valid[i] || c.keyX[i] != quartX || c.keyZ[i] != quartZ) {
            c.surface[i] = t.computeSurfaceHeight(blockX, blockZ);
            c.keyX[i] = quartX;
            c.keyZ[i] = quartZ;
            c.valid[i] = true;
        }
        return c.surface[i];
    }

    public GrandterrainBiomeSource(HolderGetter<Biome> biomeGetter) {
        this(biomeGetter, ConfigSnapshot.from(ConfigManager.getConfig()));
    }

    /** Codec entry point: persisted settings if present, else the local config. */
    public GrandterrainBiomeSource(HolderGetter<Biome> biomeGetter, java.util.Optional<ConfigSnapshot> settings) {
        this(biomeGetter, settings.orElseGet(() -> ConfigSnapshot.from(ConfigManager.getConfig())));
    }

    public GrandterrainBiomeSource(HolderGetter<Biome> biomeGetter, ConfigSnapshot config) {
        this.biomeGetter = biomeGetter;
        this.config = config;

        int seaLevel = config.seaLevel();
        int snowLine = config.snowLineBase();
        int surfaceToSnowRange = snowLine - seaLevel;

        int t0 = seaLevel + config.deepOceanOffset();
        int t1 = Math.max(t0 + 5, seaLevel + config.coastalOffset());
        int t2 = Math.max(t1 + 5, seaLevel + config.lowlandOffset());
        int t3 = Math.max(t2 + 5, seaLevel + Math.max(20, surfaceToSnowRange / 4));
        int t4 = Math.max(t3 + 5, seaLevel + Math.max(60, surfaceToSnowRange / 2));
        int t5 = Math.max(t4 + 5, seaLevel + Math.max(100, (surfaceToSnowRange * 3) / 4));
        int t6 = Math.max(t5 + 5, snowLine - 20);

        this.yDeepOcean = t0;
        this.yCoastalCliffs = t1;

        this.deepOcean = getOrFallback(biomeGetter, GrandterrainBiomes.DEEP_OCEAN, Biomes.DEEP_OCEAN);
        this.caverns = getOrFallback(biomeGetter, GrandterrainBiomes.CAVERNS, Biomes.DRIPSTONE_CAVES);
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

        this.blendY = config.biomeBlendWidth();
        this.blendClimate = config.climateBlendWidth();

        float range = t6 - t0;
        if (range > 0) {
            float scale = 2.0f / range;
            this.altParamScale = scale;
            this.altParamOffset = -1.0f - (t0 * scale);
        } else {
            Grandterrain.LOGGER.warn("Biome altitude range is zero or negative (t0={}, t6={}); all surface biomes will collapse", t0, t6);
            this.altParamScale = 0.0f;
            this.altParamOffset = 0.0f;
        }

        float cTemperate = blockYToParam(t3);
        float cAlpine = blockYToParam(t4);
        float cMountain = blockYToParam(t5);
        float cRocky = blockYToParam(t6);

        this.biomeParameters = buildBiomeParameters(cTemperate, cAlpine, cMountain, cRocky);
    }

    public void initClimate(long seed) {
        this.climateNoise = new ClimateNoise(seed);
    }

    /** Lets the biome source tell underground from underwater (see getNoiseBiome). */
    public void initTerrain(TerrainDensityFunction terrain) {
        this.terrain = terrain;
    }

    public HolderGetter<Biome> getBiomeGetter() {
        return biomeGetter;
    }

    private float blockYToParam(int blockY) {
        return blockY * altParamScale + altParamOffset;
    }

    private Climate.ParameterList<Holder<Biome>> buildBiomeParameters(
            float cTemperate, float cAlpine, float cMountain, float cRocky) {

        // Bands calibrated to the MEASURED ClimateNoise distribution (FBm
        // concentrates near 0; see mod/tools/ClimateProbe), not a uniform
        // [-1.5,1.5]. Quantile splits ~16/33/26/25% keep every temperature
        // tier populated — the old 0.5 HOT cutoff caught only 7% of the map
        // and starved deserts/savannas to under 0.5% each.
        Climate.Parameter T_FROZEN = Climate.Parameter.span(-1.5f, -0.35f);
        Climate.Parameter T_COOL = Climate.Parameter.span(-0.35f, -0.02f);
        Climate.Parameter T_WARM = Climate.Parameter.span(-0.02f, 0.25f);
        Climate.Parameter T_HOT = Climate.Parameter.span(0.25f, 1.5f);
        Climate.Parameter T_NOT_FROZEN = Climate.Parameter.span(-0.35f, 1.5f);

        Climate.Parameter C_LOW = Climate.Parameter.span(-1.5f, cTemperate);
        Climate.Parameter C_TEMPERATE = Climate.Parameter.span(cTemperate, cAlpine);
        Climate.Parameter C_ALPINE = Climate.Parameter.span(cAlpine, cMountain);
        Climate.Parameter C_MOUNTAIN = Climate.Parameter.span(cMountain, cRocky);
        Climate.Parameter C_PEAK = Climate.Parameter.span(cRocky, 1.5f);
        Climate.Parameter C_SURFACE = Climate.Parameter.span(-1.5f, cRocky);

        List<Pair<Climate.ParameterPoint, Holder<Biome>>> entries = new ArrayList<>();

        entries.add(entry(C_PEAK, PARAM_FULL, PARAM_FULL, snowPeaks));

        entries.add(entry(C_SURFACE, T_FROZEN, PARAM_FULL, tundra));

        entries.add(entry(C_MOUNTAIN, T_NOT_FROZEN, PARAM_FULL, rockyHighlands));

        entries.add(entry(C_ALPINE, T_NOT_FROZEN, PARAM_FULL, mountainPineForest));

        entries.add(entry(C_TEMPERATE, T_NOT_FROZEN,
                Climate.Parameter.span(-1.5f, 0.3f), alpineMeadow));
        entries.add(entry(C_TEMPERATE, T_NOT_FROZEN,
                Climate.Parameter.span(0.3f, 1.5f), deepValley));

        entries.add(entry(C_LOW, T_HOT, Climate.Parameter.span(-1.5f, -0.4f), volcanicRegion));
        entries.add(entry(C_LOW, T_HOT, Climate.Parameter.span(-0.4f, -0.2f), desert));
        entries.add(entry(C_LOW, T_HOT, Climate.Parameter.span(-0.2f, 0.4f), savanna));
        entries.add(entry(C_LOW, T_HOT, Climate.Parameter.span(0.4f, 1.5f), swamp));

        entries.add(entry(C_LOW, T_WARM, Climate.Parameter.span(0.4f, 1.5f), darkForest));
        entries.add(entry(C_LOW, T_WARM, Climate.Parameter.span(0.0f, 0.4f), temperateForest));
        entries.add(entry(C_LOW, T_WARM, Climate.Parameter.span(-1.5f, 0.0f), lowlandPlains));

        entries.add(entry(C_LOW, T_COOL, Climate.Parameter.span(0.2f, 1.5f), birchForest));
        entries.add(entry(C_LOW, T_COOL, Climate.Parameter.span(-1.5f, 0.2f), lowlandPlains));

        return new Climate.ParameterList<>(entries);
    }

    private static Pair<Climate.ParameterPoint, Holder<Biome>> entry(
            Climate.Parameter continentalness,
            Climate.Parameter temperature,
            Climate.Parameter humidity,
            Holder<Biome> biome) {
        return Pair.of(
                Climate.parameters(temperature, humidity, continentalness,
                        PARAM_FULL, PARAM_FULL, PARAM_FULL, 0L),
                biome);
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
                caverns, deepOcean, coastalCliffs, lowlandPlains, temperateForest,
                alpineMeadow, mountainPineForest, rockyHighlands, snowPeaks,
                deepValley, volcanicRegion,
                desert, savanna, swamp, tundra, birchForest, darkForest
        );
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        int blockY = y * 4;
        int blockX = x * 4;
        int blockZ = z * 4;

        // Anything meaningfully BELOW THE TERRAIN SURFACE is underground, and
        // gets the caverns biome (cave mobs, no ocean fog). A fixed Y cutoff
        // cannot do this: the ocean bands below only make sense for the water
        // column, but under land the same Y range is prime cave territory —
        // it was resolving to deep_ocean, which spawns nothing but drowned.
        // The margin keeps the top few blocks on the surface biome so surface
        // painting and decoration still see the real biome.
        TerrainDensityFunction t = terrain;
        // Margin 16, not 8: surfaceAt samples the quart-column CORNER (up to 3
        // blocks away) while blockY is the quart floor (up to 3 below the real
        // surface), and on steep terrain those stack — measured worst overshoot
        // 13.05 blocks, mis-typing 0.95% of land surface columns as caverns
        // (grass speckle on cliff faces, wrong mob spawns). 16 drops that to
        // 0.009%; caves more than 16 blocks down are still caverns.
        if (t != null && blockY < surfaceAt(t, x, z, blockX, blockZ) - 16.0) {
            return caverns;
        }
        if (blockY < yDeepOcean) return deepOcean;
        if (blockY < yCoastalCliffs) return coastalCliffs;

        int jy = blockY + altitudeJitter(blockX, blockZ);
        float altParam = blockYToParam(jy);

        float temp = 0.25f;
        float humid = 0.15f;

        ClimateNoise cn = climateNoise;
        if (cn != null) {
            temp = (float) (cn.temperature(blockX, blockZ)
                    + climateJitter(blockX, blockZ, 0));
            humid = (float) (cn.humidity(blockX, blockZ)
                    + climateJitter(blockX, blockZ, 1));
        }

        Climate.TargetPoint target = Climate.target(
                Climate.quantizeCoord(temp),
                Climate.quantizeCoord(humid),
                Climate.quantizeCoord(altParam),
                0L, 0L, 0L);

        return biomeParameters.findValue(target);
    }

    /**
     * Boundary dithers. One avalanche hash per quart cell; altitude, temperature
     * and humidity each take a DISJOINT byte so they are uncorrelated. The old
     * linear hashes repeated every 28 blocks in Z / 44 in X, tiled exactly
     * every 1024 blocks, and made the temperature and humidity jitter the
     * SAME field shifted 356 blocks — visible plaid on biome boundaries.
     */
    private int altitudeJitter(int blockX, int blockZ) {
        if (blendY <= 0) return 0;
        int hash = WorldgenHash.mix2(blockX >> 2, blockZ >> 2) >>> 24;
        return (hash * (2 * blendY + 1)) / 256 - blendY;
    }

    private double climateJitter(int blockX, int blockZ, int axis) {
        if (blendClimate <= 0.0) return 0.0;
        int h = WorldgenHash.mix2(blockX >> 2, blockZ >> 2);
        int hash = (axis == 0 ? (h >>> 16) : (h >>> 8)) & 0xFF;
        return ((double) hash / 256.0 - 0.5) * 2.0 * blendClimate;
    }
}
