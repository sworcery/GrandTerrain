package com.grandterrain.worldgen;

import com.grandterrain.config.ConfigManager;
import com.grandterrain.config.ConfigSnapshot;
import com.grandterrain.worldgen.biome.GrandterrainBiomeSource;
import com.grandterrain.worldgen.biome.GrandterrainBiomes;
import com.grandterrain.worldgen.cave.CarveResult;
import com.grandterrain.worldgen.cave.CaveContributor;
import com.grandterrain.worldgen.noise.GrandterrainNoiseRouter;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GrandterrainChunkGenerator extends ChunkGenerator {

    public static final MapCodec<GrandterrainChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(gen -> gen.biomeSource)
            ).apply(instance, GrandterrainChunkGenerator::new)
    );

    private static final int INTERP_H = 4;
    private static final int INTERP_V = 8;
    private static final double INV_INTERP_H = 1.0 / INTERP_H;
    private static final double INV_INTERP_V = 1.0 / INTERP_V;
    private static final int SAMPLES_XZ = 16 / INTERP_H + 1;

    private static final int BEDROCK_LAYERS = 5;
    private static final int DEEPSLATE_TRANSITION_THICKNESS = 64;

    private final ConfigSnapshot config;
    private volatile GrandterrainNoiseRouter noiseRouter;

    public GrandterrainChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
        this.config = ConfigSnapshot.from(ConfigManager.getConfig());
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public ChunkGeneratorStructureState createState(
            HolderLookup<StructureSet> structureSetLookup, RandomState randomState, long seed) {
        this.noiseRouter = new GrandterrainNoiseRouter(seed, config);

        if (biomeSource instanceof GrandterrainBiomeSource gbs) {
            gbs.initClimate(seed);
        }

        return super.createState(structureSetLookup, randomState, seed);
    }

    private GrandterrainNoiseRouter router() {
        GrandterrainNoiseRouter r = noiseRouter;
        if (r == null) {
            throw new IllegalStateException(
                    "GrandterrainChunkGenerator accessed before createState(); seed not initialized");
        }
        return r;
    }

    public ConfigSnapshot getConfigSnapshot() {
        return config;
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk) {
    }

    // -------------------------------------------------------------------------
    // Surface painting
    // -------------------------------------------------------------------------

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
        GrandterrainNoiseRouter r = router();
        int chunkX = chunk.getPos().getMinBlockX();
        int chunkZ = chunk.getPos().getMinBlockZ();

        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight() - 1;
        int seaLevel = config.seaLevel();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkX + localX;
                int worldZ = chunkZ + localZ;

                int surfaceY = findSurfaceY(chunk, localX, localZ, minY, maxY, pos);
                if (surfaceY <= minY) continue;

                applySurfaceRules(chunk, localX, localZ, surfaceY, seaLevel,
                        worldX, worldZ, pos);
            }
        }

        Heightmap.primeHeightmaps(chunk,
                EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }

    private int findSurfaceY(ChunkAccess chunk, int localX, int localZ, int minY, int maxY,
                              BlockPos.MutableBlockPos pos) {
        for (int y = maxY; y >= minY; y--) {
            pos.set(localX, y, localZ);
            BlockState state = chunk.getBlockState(pos);
            if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)) {
                return y;
            }
        }
        return minY;
    }

    private void applySurfaceRules(ChunkAccess chunk, int localX, int localZ,
                                    int surfaceY, int seaLevel,
                                    int worldX, int worldZ,
                                    BlockPos.MutableBlockPos pos) {
        final int snowLine = config.snowLineBase();
        final int minY = chunk.getMinY();

        if (surfaceY < seaLevel - 5) {
            applyOceanFloor(chunk, localX, localZ, surfaceY, minY, pos);
            return;
        }
        if (surfaceY < seaLevel + 3) {
            applyBeach(chunk, localX, localZ, surfaceY, minY, pos);
            return;
        }

        int biomeQueryY = config.seaLevel() + 10;
        Holder<Biome> biome = chunk.getNoiseBiome(worldX >> 2, biomeQueryY >> 2, worldZ >> 2);

        if (biome.is(GrandterrainBiomes.DESERT)) {
            applyDesertSurface(chunk, localX, localZ, surfaceY, minY, pos);
            return;
        }
        if (biome.is(GrandterrainBiomes.SWAMP)) {
            applySwampSurface(chunk, localX, localZ, surfaceY, minY, worldX, worldZ, pos);
            return;
        }
        if (biome.is(GrandterrainBiomes.TUNDRA)) {
            applyTundraSurface(chunk, localX, localZ, surfaceY, minY, worldX, worldZ, pos);
            return;
        }
        if (biome.is(GrandterrainBiomes.SAVANNA)) {
            applySavannaSurface(chunk, localX, localZ, surfaceY, minY, worldX, worldZ, pos);
            return;
        }
        if (biome.is(GrandterrainBiomes.DARK_FOREST)) {
            applyDarkForestSurface(chunk, localX, localZ, surfaceY, minY, worldX, worldZ, pos);
            return;
        }
        if (biome.is(GrandterrainBiomes.SNOW_PEAKS)) {
            applySnowPeaks(chunk, localX, localZ, surfaceY, minY, pos);
            return;
        }

        if (surfaceY > snowLine) {
            applySnowPeaks(chunk, localX, localZ, surfaceY, minY, pos);
        } else if (surfaceY > snowLine - 80) {
            applySnowTransition(chunk, localX, localZ, surfaceY, minY, snowLine, worldX, worldZ, pos);
        } else if (surfaceY > snowLine - 160) {
            applyRockyHighlands(chunk, localX, localZ, surfaceY, minY, worldX, worldZ, pos);
        } else {
            applyGrassland(chunk, localX, localZ, surfaceY, minY, pos);
        }
    }

    private void applyOceanFloor(ChunkAccess chunk, int localX, int localZ,
                                  int surfaceY, int minY, BlockPos.MutableBlockPos pos) {
        for (int depth = 0; depth < 3 && surfaceY - depth >= minY; depth++) {
            pos.set(localX, surfaceY - depth, localZ);
            chunk.setBlockState(pos, depth == 0 ? Blocks.GRAVEL.defaultBlockState() :
                    Blocks.SANDSTONE.defaultBlockState(), 0);
        }
    }

    private void applyBeach(ChunkAccess chunk, int localX, int localZ,
                             int surfaceY, int minY, BlockPos.MutableBlockPos pos) {
        for (int depth = 0; depth < 4 && surfaceY - depth >= minY; depth++) {
            pos.set(localX, surfaceY - depth, localZ);
            chunk.setBlockState(pos, depth < 2 ? Blocks.SAND.defaultBlockState() :
                    Blocks.SANDSTONE.defaultBlockState(), 0);
        }
    }

    private void applySnowPeaks(ChunkAccess chunk, int localX, int localZ,
                                 int surfaceY, int minY, BlockPos.MutableBlockPos pos) {
        pos.set(localX, surfaceY, localZ);
        chunk.setBlockState(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 0);
        if (surfaceY + 1 <= minY + chunk.getHeight() - 1) {
            pos.set(localX, surfaceY + 1, localZ);
            chunk.setBlockState(pos, Blocks.SNOW.defaultBlockState(), 0);
        }
    }

    private void applySnowTransition(ChunkAccess chunk, int localX, int localZ,
                                      int surfaceY, int minY, int snowLine,
                                      int worldX, int worldZ, BlockPos.MutableBlockPos pos) {
        pos.set(localX, surfaceY, localZ);
        boolean isSnow = ((worldX * 31 + worldZ * 17) & 3) == 0;
        if (isSnow && surfaceY > snowLine - 40) {
            chunk.setBlockState(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 0);
        } else {
            chunk.setBlockState(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 0);
            for (int depth = 1; depth < 4 && surfaceY - depth >= minY; depth++) {
                pos.set(localX, surfaceY - depth, localZ);
                chunk.setBlockState(pos, Blocks.DIRT.defaultBlockState(), 0);
            }
        }
    }

    private void applyRockyHighlands(ChunkAccess chunk, int localX, int localZ,
                                      int surfaceY, int minY,
                                      int worldX, int worldZ, BlockPos.MutableBlockPos pos) {
        pos.set(localX, surfaceY, localZ);
        boolean isGravel = ((worldX * 13 + worldZ * 7) & 7) == 0;
        if (isGravel) {
            chunk.setBlockState(pos, Blocks.GRAVEL.defaultBlockState(), 0);
        }
    }

    private void applyGrassland(ChunkAccess chunk, int localX, int localZ,
                                 int surfaceY, int minY, BlockPos.MutableBlockPos pos) {
        pos.set(localX, surfaceY, localZ);
        chunk.setBlockState(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 0);
        for (int depth = 1; depth < 4 && surfaceY - depth >= minY; depth++) {
            pos.set(localX, surfaceY - depth, localZ);
            chunk.setBlockState(pos, Blocks.DIRT.defaultBlockState(), 0);
        }
    }

    private void applyDesertSurface(ChunkAccess chunk, int localX, int localZ,
                                     int surfaceY, int minY, BlockPos.MutableBlockPos pos) {
        for (int depth = 0; depth < 4 && surfaceY - depth >= minY; depth++) {
            pos.set(localX, surfaceY - depth, localZ);
            chunk.setBlockState(pos, depth < 3 ? Blocks.SAND.defaultBlockState() :
                    Blocks.SANDSTONE.defaultBlockState(), 0);
        }
    }

    private void applySwampSurface(ChunkAccess chunk, int localX, int localZ,
                                    int surfaceY, int minY,
                                    int worldX, int worldZ, BlockPos.MutableBlockPos pos) {
        pos.set(localX, surfaceY, localZ);
        chunk.setBlockState(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 0);
        for (int depth = 1; depth < 4 && surfaceY - depth >= minY; depth++) {
            pos.set(localX, surfaceY - depth, localZ);
            boolean isClay = ((worldX * 11 + worldZ * 23) & 7) == 0;
            chunk.setBlockState(pos, isClay ? Blocks.CLAY.defaultBlockState() :
                    Blocks.DIRT.defaultBlockState(), 0);
        }
    }

    private void applyTundraSurface(ChunkAccess chunk, int localX, int localZ,
                                     int surfaceY, int minY,
                                     int worldX, int worldZ, BlockPos.MutableBlockPos pos) {
        pos.set(localX, surfaceY, localZ);
        boolean isSnow = ((worldX * 31 + worldZ * 17) & 3) < 2;
        if (isSnow) {
            chunk.setBlockState(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 0);
            if (surfaceY + 1 <= minY + chunk.getHeight() - 1) {
                pos.set(localX, surfaceY + 1, localZ);
                chunk.setBlockState(pos, Blocks.SNOW.defaultBlockState(), 0);
            }
        } else {
            boolean isPodzol = ((worldX * 7 + worldZ * 19) & 3) == 0;
            chunk.setBlockState(pos, isPodzol ? Blocks.PODZOL.defaultBlockState() :
                    Blocks.COARSE_DIRT.defaultBlockState(), 0);
        }
        for (int depth = 1; depth < 3 && surfaceY - depth >= minY; depth++) {
            pos.set(localX, surfaceY - depth, localZ);
            chunk.setBlockState(pos, Blocks.DIRT.defaultBlockState(), 0);
        }
    }

    private void applySavannaSurface(ChunkAccess chunk, int localX, int localZ,
                                      int surfaceY, int minY,
                                      int worldX, int worldZ, BlockPos.MutableBlockPos pos) {
        pos.set(localX, surfaceY, localZ);
        boolean isCoarse = ((worldX * 13 + worldZ * 29) & 7) == 0;
        chunk.setBlockState(pos, isCoarse ? Blocks.COARSE_DIRT.defaultBlockState() :
                Blocks.GRASS_BLOCK.defaultBlockState(), 0);
        for (int depth = 1; depth < 4 && surfaceY - depth >= minY; depth++) {
            pos.set(localX, surfaceY - depth, localZ);
            chunk.setBlockState(pos, Blocks.DIRT.defaultBlockState(), 0);
        }
    }

    private void applyDarkForestSurface(ChunkAccess chunk, int localX, int localZ,
                                         int surfaceY, int minY,
                                         int worldX, int worldZ, BlockPos.MutableBlockPos pos) {
        pos.set(localX, surfaceY, localZ);
        boolean isPodzol = ((worldX * 17 + worldZ * 31) & 7) < 2;
        chunk.setBlockState(pos, isPodzol ? Blocks.PODZOL.defaultBlockState() :
                Blocks.GRASS_BLOCK.defaultBlockState(), 0);
        for (int depth = 1; depth < 4 && surfaceY - depth >= minY; depth++) {
            pos.set(localX, surfaceY - depth, localZ);
            chunk.setBlockState(pos, Blocks.DIRT.defaultBlockState(), 0);
        }
    }

    // -------------------------------------------------------------------------
    // Terrain + caves
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                         StructureManager structureManager,
                                                         ChunkAccess chunk) {
        generateTerrain(chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    private void generateTerrain(ChunkAccess chunk) {
        GrandterrainNoiseRouter r = router();

        int chunkX = chunk.getPos().getMinBlockX();
        int chunkZ = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight() - 1;
        int seaLevel = config.seaLevel();

        int deepslateTop = minY + DEEPSLATE_TRANSITION_THICKNESS;

        int totalHeight = maxY - minY + 1;
        int samplesY = totalHeight / INTERP_V + 1;

        double[][][] densityGrid = new double[SAMPLES_XZ][SAMPLES_XZ][samplesY];
        for (int sx = 0; sx < SAMPLES_XZ; sx++) {
            for (int sz = 0; sz < SAMPLES_XZ; sz++) {
                int worldX = chunkX + sx * INTERP_H;
                int worldZ = chunkZ + sz * INTERP_H;
                double[] column = densityGrid[sx][sz];
                for (int sy = 0; sy < samplesY; sy++) {
                    int worldY = minY + sy * INTERP_V;
                    column[sy] = r.getTerrainDensity().compute(worldX, worldY, worldZ);
                }
            }
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            int cellX = localX >> 2;
            int nextCellX = Math.min(cellX + 1, SAMPLES_XZ - 1);
            double fracX = (localX & 3) * INV_INTERP_H;
            int worldX = chunkX + localX;

            for (int localZ = 0; localZ < 16; localZ++) {
                int cellZ = localZ >> 2;
                int nextCellZ = Math.min(cellZ + 1, SAMPLES_XZ - 1);
                double fracZ = (localZ & 3) * INV_INTERP_H;
                int worldZ = chunkZ + localZ;

                double[] col00 = densityGrid[cellX][cellZ];
                double[] col10 = densityGrid[nextCellX][cellZ];
                double[] col01 = densityGrid[cellX][nextCellZ];
                double[] col11 = densityGrid[nextCellX][nextCellZ];

                for (int y = minY; y <= maxY; y++) {
                    int cellY = (y - minY) / INTERP_V;
                    int nextCellY = Math.min(cellY + 1, samplesY - 1);
                    double fracY = ((y - minY) % INTERP_V) * INV_INTERP_V;

                    double d000 = col00[cellY];
                    double d100 = col10[cellY];
                    double d010 = col00[nextCellY];
                    double d110 = col10[nextCellY];
                    double d001 = col01[cellY];
                    double d101 = col11[cellY];
                    double d011 = col01[nextCellY];
                    double d111 = col11[nextCellY];

                    double d00 = lerp(fracX, d000, d100);
                    double d10 = lerp(fracX, d010, d110);
                    double d01 = lerp(fracX, d001, d101);
                    double d11 = lerp(fracX, d011, d111);

                    double d0 = lerp(fracY, d00, d10);
                    double d1 = lerp(fracY, d01, d11);

                    double density = lerp(fracZ, d0, d1);

                    pos.set(localX, y, localZ);

                    if (y < minY + BEDROCK_LAYERS) {
                        chunk.setBlockState(pos, Blocks.BEDROCK.defaultBlockState(), 0);
                    } else if (density > 0) {
                        CarveResult carve = sampleCaves(r, worldX, y, worldZ);

                        switch (carve) {
                            case CARVE_WATER -> chunk.setBlockState(pos,
                                    Blocks.WATER.defaultBlockState(), 0);
                            case CARVE_AIR -> {
                                if (y < minY + 20) {
                                    chunk.setBlockState(pos, Blocks.LAVA.defaultBlockState(), 0);
                                }
                            }
                            case SOLID -> chunk.setBlockState(pos,
                                    selectStoneBlock(y, minY, deepslateTop, worldX, worldZ),
                                    0);
                        }
                    } else if (y < seaLevel) {
                        chunk.setBlockState(pos, Blocks.WATER.defaultBlockState(), 0);
                    }
                }
            }
        }

        applySurfaceRivers(chunk, r, chunkX, chunkZ, minY, maxY, seaLevel);

        Heightmap.primeHeightmaps(chunk,
                EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }

    private static BlockState selectStoneBlock(int y, int minY, int deepslateTop,
                                                int worldX, int worldZ) {
        if (y < minY + BEDROCK_LAYERS + 3) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }

        if (y < deepslateTop) {
            double progress = (double) (y - minY) / DEEPSLATE_TRANSITION_THICKNESS;
            int hash = (worldX * 31 + worldZ * 17 + y * 13) & 255;
            double threshold = progress * progress;
            if (hash < (int) (threshold * 255)) {
                return Blocks.STONE.defaultBlockState();
            }
            return Blocks.DEEPSLATE.defaultBlockState();
        }

        return Blocks.STONE.defaultBlockState();
    }

    private void applySurfaceRivers(ChunkAccess chunk, GrandterrainNoiseRouter r,
                                     int chunkX, int chunkZ, int minY, int maxY, int seaLevel) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkX + localX;
                int worldZ = chunkZ + localZ;

                double riverCarving = r.getSurfaceRivers().sampleRiverCarving(worldX, worldZ);
                if (riverCarving <= 0) continue;

                int carveDepth = (int) Math.ceil(riverCarving);

                int surfaceY = minY - 1;
                int solidCount = 0;
                for (int y = maxY; y >= minY; y--) {
                    pos.set(localX, y, localZ);
                    BlockState state = chunk.getBlockState(pos);
                    if (!state.isAir() && !state.is(Blocks.WATER)) {
                        if (surfaceY < 0) surfaceY = y;
                        solidCount++;
                        if (solidCount >= carveDepth + 2) break;
                    } else if (surfaceY >= 0) {
                        surfaceY = -1;
                        break;
                    }
                }
                if (surfaceY < 0 || solidCount < carveDepth + 2) continue;

                double waterLevel = r.getSurfaceRivers()
                        .getRiverWaterLevel(worldX, worldZ, surfaceY, carveDepth);

                for (int d = 0; d <= carveDepth; d++) {
                    int y = surfaceY - d;
                    if (y < minY) break;

                    pos.set(localX, y, localZ);
                    if (y <= waterLevel) {
                        chunk.setBlockState(pos, Blocks.WATER.defaultBlockState(), 0);
                    } else {
                        chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), 0);
                    }
                }

                int bedY = surfaceY - carveDepth - 1;
                if (bedY >= minY) {
                    pos.set(localX, bedY, localZ);
                    boolean isClay = ((worldX * 7 + worldZ * 13) & 3) == 0;
                    chunk.setBlockState(pos, isClay ? Blocks.CLAY.defaultBlockState() :
                            Blocks.GRAVEL.defaultBlockState(), 0);
                }
            }
        }
    }

    private static CarveResult sampleCaves(GrandterrainNoiseRouter r,
                                            int worldX, int y, int worldZ) {
        for (CaveContributor cave : r.getCaves()) {
            if (y < cave.minY() || y > cave.maxY()) continue;
            CarveResult result = cave.sample(worldX, y, worldZ);
            if (result != CarveResult.SOLID) return result;
        }
        return CarveResult.SOLID;
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    /**
     * Surface height as actually built by generateTerrain. Density is linear in Y,
     * so the trilinear density interpolation over the 4-block XZ sample grid yields
     * a surface that is the bilinear interpolation of computeSurfaceHeight at the
     * surrounding grid corners. Using the analytic height here instead would
     * disagree with the generated terrain by several blocks on steep ridges,
     * making structures float or sink.
     */
    private double interpolatedSurfaceHeight(GrandterrainNoiseRouter r, int x, int z) {
        int gx0 = Math.floorDiv(x, INTERP_H) * INTERP_H;
        int gz0 = Math.floorDiv(z, INTERP_H) * INTERP_H;
        var f = r.getTerrainDensity();
        double h00 = f.computeSurfaceHeight(gx0, gz0);
        double h10 = f.computeSurfaceHeight(gx0 + INTERP_H, gz0);
        double h01 = f.computeSurfaceHeight(gx0, gz0 + INTERP_H);
        double h11 = f.computeSurfaceHeight(gx0 + INTERP_H, gz0 + INTERP_H);
        double fx = (x - gx0) * INV_INTERP_H;
        double fz = (z - gz0) * INV_INTERP_H;
        return lerp(fz, lerp(fx, h00, h10), lerp(fx, h01, h11));
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
                             RandomState randomState) {
        GrandterrainNoiseRouter r = router();
        int minY = level.getMinY();
        int maxY = minY + level.getHeight() - 1;

        double surfaceH = interpolatedSurfaceHeight(r, x, z);
        // First free block above the surface: solid iff y < surfaceH, so the
        // first free Y is ceil(surfaceH) (== floor+1 except at exact integers).
        int est = (int) Math.ceil(surfaceH);
        if (est < minY) return minY;
        if (est > maxY) return maxY;
        return est;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        GrandterrainNoiseRouter r = router();
        int minY = level.getMinY();
        int height = level.getHeight();
        BlockState[] states = new BlockState[height];
        int seaLevel = config.seaLevel();
        int deepslateTop = minY + DEEPSLATE_TRANSITION_THICKNESS;

        double surfaceH = interpolatedSurfaceHeight(r, x, z);

        for (int i = 0; i < height; i++) {
            int y = minY + i;
            if (y < minY + BEDROCK_LAYERS) {
                states[i] = Blocks.BEDROCK.defaultBlockState();
            } else if (y < surfaceH) { // matches generateTerrain: solid iff density = surfaceH - y > 0
                states[i] = y < deepslateTop
                        ? Blocks.DEEPSLATE.defaultBlockState()
                        : Blocks.STONE.defaultBlockState();
            } else if (y < seaLevel) {
                states[i] = Blocks.WATER.defaultBlockState();
            } else {
                states[i] = Blocks.AIR.defaultBlockState();
            }
        }

        return new NoiseColumn(minY, states);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("GrandTerrain Generator");
        info.add("Mountain Scale: " + config.mountainHeightScale());
        GrandterrainNoiseRouter r = noiseRouter;
        if (r != null) {
            info.add("Seed: " + r.getSeed());
            double cont = r.sampleContinentalness(pos.getX(), pos.getZ());
            info.add(String.format("Continentalness: %.3f", cont));
        }
    }

    @Override
    public int getMinY() {
        return config.worldMinY();
    }

    @Override
    public int getGenDepth() {
        return config.worldHeight();
    }

    @Override
    public int getSeaLevel() {
        return config.seaLevel();
    }
}
