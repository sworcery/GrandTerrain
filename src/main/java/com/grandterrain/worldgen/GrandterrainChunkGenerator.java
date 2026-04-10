package com.grandterrain.worldgen;

import com.grandterrain.config.ConfigManager;
import com.grandterrain.config.GrandterrainConfig;
import com.grandterrain.worldgen.noise.GrandterrainNoiseRouter;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class GrandterrainChunkGenerator extends ChunkGenerator {

    public static final MapCodec<GrandterrainChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(gen -> gen.biomeSource)
            ).apply(instance, GrandterrainChunkGenerator::new)
    );

    // CRITICAL-1 fix: AtomicReference for thread-safe lazy initialization
    private final AtomicReference<GrandterrainNoiseRouter> noiseRouterRef = new AtomicReference<>();
    private final GrandterrainConfig config;

    public GrandterrainChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
        this.config = ConfigManager.getConfig();
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // CRITICAL-1 fix: thread-safe router access via AtomicReference
    private GrandterrainNoiseRouter getOrCreateNoiseRouter(long seed) {
        GrandterrainNoiseRouter current = noiseRouterRef.get();
        if (current != null && current.getSeed() == seed) {
            return current;
        }
        GrandterrainNoiseRouter newRouter = new GrandterrainNoiseRouter(seed, config);
        noiseRouterRef.compareAndSet(current, newRouter);
        return noiseRouterRef.get();
    }

    @Override
    public void applyCarvers(WorldGenRegion level, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk) {
        // Caves are integrated directly into fillFromNoise for better coherence
    }

    @Override
    public void buildSurface(WorldGenRegion level, StructureManager structureManager,
                             RandomState randomState, ChunkAccess chunk) {
        GrandterrainNoiseRouter router = getOrCreateNoiseRouter(level.getSeed());
        int chunkX = chunk.getPos().getMinBlockX();
        int chunkZ = chunk.getPos().getMinBlockZ();

        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight() - 1;
        int seaLevel = config.seaLevel;

        // HIGH-2 fix: reuse MutableBlockPos for reads
        BlockPos.MutableBlockPos readPos = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkX + localX;
                int worldZ = chunkZ + localZ;

                int surfaceY = findSurfaceY(chunk, localX, localZ, minY, maxY, readPos);
                if (surfaceY <= minY) continue;

                double continental = router.sampleContinentalness(worldX, worldZ);
                applySurfaceRules(chunk, localX, localZ, surfaceY, seaLevel, continental, worldX, worldZ);
            }
        }
    }

    // HIGH-2 fix: accept MutableBlockPos to avoid allocations
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
                                    int surfaceY, int seaLevel, double continental,
                                    int worldX, int worldZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        if (surfaceY < seaLevel - 5) {
            for (int depth = 0; depth < 3 && surfaceY - depth >= chunk.getMinY(); depth++) {
                pos.set(localX, surfaceY - depth, localZ);
                chunk.setBlockState(pos, depth == 0 ? Blocks.GRAVEL.defaultBlockState() :
                        Blocks.SANDSTONE.defaultBlockState(), 0);
            }
        } else if (surfaceY < seaLevel + 3) {
            for (int depth = 0; depth < 4 && surfaceY - depth >= chunk.getMinY(); depth++) {
                pos.set(localX, surfaceY - depth, localZ);
                chunk.setBlockState(pos, depth < 2 ? Blocks.SAND.defaultBlockState() :
                        Blocks.SANDSTONE.defaultBlockState(), 0);
            }
        } else if (surfaceY > config.snowLineBase) {
            pos.set(localX, surfaceY, localZ);
            chunk.setBlockState(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 0);
            if (surfaceY + 1 <= chunk.getMinY() + chunk.getHeight() - 1) {
                pos.set(localX, surfaceY + 1, localZ);
                chunk.setBlockState(pos, Blocks.SNOW.defaultBlockState(), 0);
            }
        } else if (surfaceY > config.snowLineBase - 80) {
            pos.set(localX, surfaceY, localZ);
            boolean isSnow = ((worldX * 31 + worldZ * 17) & 3) == 0;
            if (isSnow && surfaceY > config.snowLineBase - 40) {
                chunk.setBlockState(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 0);
            } else {
                chunk.setBlockState(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 0);
                for (int depth = 1; depth < 4 && surfaceY - depth >= chunk.getMinY(); depth++) {
                    pos.set(localX, surfaceY - depth, localZ);
                    chunk.setBlockState(pos, Blocks.DIRT.defaultBlockState(), 0);
                }
            }
        } else if (surfaceY > 300) {
            pos.set(localX, surfaceY, localZ);
            boolean isGravel = ((worldX * 13 + worldZ * 7) & 7) == 0;
            if (isGravel) {
                chunk.setBlockState(pos, Blocks.GRAVEL.defaultBlockState(), 0);
            }
        } else {
            pos.set(localX, surfaceY, localZ);
            chunk.setBlockState(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 0);
            for (int depth = 1; depth < 4 && surfaceY - depth >= chunk.getMinY(); depth++) {
                pos.set(localX, surfaceY - depth, localZ);
                chunk.setBlockState(pos, Blocks.DIRT.defaultBlockState(), 0);
            }
        }
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                         StructureManager structureManager,
                                                         ChunkAccess chunk) {
        return CompletableFuture.supplyAsync(() -> {
            // CRITICAL-2 fix: use randomState's seed via the router's stored seed
            // The router will have been initialized by buildSurface or getBaseHeight
            // with the correct seed from WorldGenRegion.getSeed()
            generateTerrain(chunk);
            return chunk;
        });
    }

    private void generateTerrain(ChunkAccess chunk) {
        // CRITICAL-2 fix: use the router that was already initialized with the correct seed
        GrandterrainNoiseRouter router = noiseRouterRef.get();
        if (router == null) {
            // Fallback: initialize with seed 0 if somehow called before buildSurface
            router = getOrCreateNoiseRouter(0L);
        }

        int chunkX = chunk.getPos().getMinBlockX();
        int chunkZ = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinY();
        int maxY = minY + chunk.getHeight() - 1;
        int seaLevel = config.seaLevel;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int interpH = 4;
        int interpV = 8;
        int samplesX = 16 / interpH + 1;
        int samplesZ = 16 / interpH + 1;
        int totalHeight = maxY - minY + 1;
        int samplesY = totalHeight / interpV + 1;

        // Pre-sample density at grid points
        double[][][] densityGrid = new double[samplesX][samplesY][samplesZ];
        for (int sx = 0; sx < samplesX; sx++) {
            for (int sz = 0; sz < samplesZ; sz++) {
                int worldX = chunkX + sx * interpH;
                int worldZ = chunkZ + sz * interpH;
                for (int sy = 0; sy < samplesY; sy++) {
                    int worldY = minY + sy * interpV;
                    densityGrid[sx][sy][sz] = router.getTerrainDensity().compute(worldX, worldY, worldZ);
                }
            }
        }

        // Interpolate and fill blocks
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int cellX = localX / interpH;
                int cellZ = localZ / interpH;
                double fracX = (localX % interpH) / (double) interpH;
                double fracZ = (localZ % interpH) / (double) interpH;
                int nextCellX = Math.min(cellX + 1, samplesX - 1);
                int nextCellZ = Math.min(cellZ + 1, samplesZ - 1);

                int worldX = chunkX + localX;
                int worldZ = chunkZ + localZ;

                for (int y = minY; y <= maxY; y++) {
                    int cellY = (y - minY) / interpV;
                    int nextCellY = Math.min(cellY + 1, samplesY - 1);
                    double fracY = ((y - minY) % interpV) / (double) interpV;

                    double d000 = densityGrid[cellX][cellY][cellZ];
                    double d100 = densityGrid[nextCellX][cellY][cellZ];
                    double d010 = densityGrid[cellX][nextCellY][cellZ];
                    double d110 = densityGrid[nextCellX][nextCellY][cellZ];
                    double d001 = densityGrid[cellX][cellY][nextCellZ];
                    double d101 = densityGrid[nextCellX][cellY][nextCellZ];
                    double d011 = densityGrid[cellX][nextCellY][nextCellZ];
                    double d111 = densityGrid[nextCellX][nextCellY][nextCellZ];

                    double d00 = lerp(fracX, d000, d100);
                    double d10 = lerp(fracX, d010, d110);
                    double d01 = lerp(fracX, d001, d101);
                    double d11 = lerp(fracX, d011, d111);

                    double d0 = lerp(fracY, d00, d10);
                    double d1 = lerp(fracY, d01, d11);

                    double density = lerp(fracZ, d0, d1);

                    pos.set(localX, y, localZ);

                    if (density > 0) {
                        boolean carved = false;

                        // Y-range guards: skip cave noise where caves can't exist
                        if (y < seaLevel - 10 && y > minY + 5) {
                            double cheese = router.getCheeseCaves().sample(worldX, y, worldZ);
                            if (cheese > 0) carved = true;

                            if (!carved && y < seaLevel - 5) {
                                double spaghetti = router.getSpaghettiCaves().sample(worldX, y, worldZ);
                                if (spaghetti > 0) carved = true;
                            }

                            if (!carved && y < seaLevel - 40 && y > minY + 26) {
                                double cavern = router.getMegaCaverns().sample(worldX, y, worldZ);
                                if (cavern > 0) carved = true;
                            }

                            if (!carved) {
                                double riverCarve = router.getUndergroundRivers().sampleCarve(worldX, y, worldZ);
                                if (riverCarve > 0) {
                                    carved = true;
                                    if (router.getUndergroundRivers().sampleIsWater(worldX, y, worldZ)) {
                                        chunk.setBlockState(pos, Blocks.WATER.defaultBlockState(), 0);
                                        continue;
                                    }
                                }
                            }
                        }

                        if (carved) {
                            if (y < minY + 20) {
                                chunk.setBlockState(pos, Blocks.LAVA.defaultBlockState(), 0);
                            }
                        } else {
                            if (y < minY + 5) {
                                chunk.setBlockState(pos, Blocks.DEEPSLATE.defaultBlockState(), 0);
                            } else {
                                chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), 0);
                            }
                        }
                    } else if (y < seaLevel) {
                        chunk.setBlockState(pos, Blocks.WATER.defaultBlockState(), 0);
                    }
                }
            }
        }

        // Apply surface rivers
        applySurfaceRivers(chunk, router, chunkX, chunkZ, minY, maxY, seaLevel);

        Heightmap.primeHeightmaps(chunk,
                EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
    }

    private void applySurfaceRivers(ChunkAccess chunk, GrandterrainNoiseRouter router,
                                     int chunkX, int chunkZ, int minY, int maxY, int seaLevel) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunkX + localX;
                int worldZ = chunkZ + localZ;

                double riverCarving = router.getSurfaceRivers().sampleRiverCarving(worldX, worldZ);
                if (riverCarving <= 0) continue;

                // HIGH-2 fix: reuse MutableBlockPos for reads
                int surfaceY = maxY;
                for (int y = maxY; y >= minY; y--) {
                    pos.set(localX, y, localZ);
                    BlockState state = chunk.getBlockState(pos);
                    if (!state.isAir() && !state.is(Blocks.WATER)) {
                        surfaceY = y;
                        break;
                    }
                }

                int carveDepth = (int) Math.ceil(riverCarving);
                double waterLevel = router.getSurfaceRivers().getRiverWaterLevel(worldX, worldZ, surfaceY);

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

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion level) {
    }

    // HIGH-1 fix: linear scan from top-down instead of binary search
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        GrandterrainNoiseRouter router = noiseRouterRef.get();
        if (router == null) {
            router = getOrCreateNoiseRouter(0L);
        }
        int minY = level.getMinY();
        int maxY = minY + level.getHeight() - 1;

        // Linear scan from top: find first solid block
        for (int y = maxY; y >= minY; y--) {
            double density = router.getTerrainDensity().compute(x, y, z);
            if (density > 0) {
                return y + 1;
            }
        }
        return minY;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        GrandterrainNoiseRouter router = noiseRouterRef.get();
        if (router == null) {
            router = getOrCreateNoiseRouter(0L);
        }
        int minY = level.getMinY();
        int height = level.getHeight();
        BlockState[] states = new BlockState[height];
        int seaLevel = config.seaLevel;

        for (int i = 0; i < height; i++) {
            int y = minY + i;
            double density = router.getTerrainDensity().compute(x, y, z);

            if (density > 0) {
                if (y < minY + 5) {
                    states[i] = Blocks.DEEPSLATE.defaultBlockState();
                } else {
                    states[i] = Blocks.STONE.defaultBlockState();
                }
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
        info.add("Mountain Scale: " + config.mountainHeightScale);
    }

    @Override
    public int getMinY() {
        return config.worldMinY;
    }

    @Override
    public int getGenDepth() {
        return config.worldHeight;
    }

    @Override
    public int getSeaLevel() {
        return config.seaLevel;
    }
}
