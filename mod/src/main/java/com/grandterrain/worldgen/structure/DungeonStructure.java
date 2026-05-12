package com.grandterrain.worldgen.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Optional;

public class DungeonStructure extends Structure {

    public static final MapCodec<DungeonStructure> CODEC = simpleCodec(DungeonStructure::new);

    public DungeonStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return GrandterrainStructures.DUNGEON;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (context.chunkGenerator() instanceof com.grandterrain.worldgen.GrandterrainChunkGenerator gen
                && !gen.getConfigSnapshot().enableDungeons()) {
            return Optional.empty();
        }

        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        int surfaceY = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());

        int minY = context.heightAccessor().getMinY();
        if (surfaceY <= minY + 5) return Optional.empty();

        int depthOffset = 30 + context.random().nextInt(40);
        int y = surfaceY - depthOffset;
        int numFloors = 2 + context.random().nextInt(2);
        int totalDepth = (numFloors - 1) * FLOOR_SPACING + ROOM_HEIGHT + 2;

        if (y - totalDepth < minY + 5) y = minY + 5 + totalDepth;
        if (y > surfaceY - 15) return Optional.empty();

        int layoutSeed = context.random().nextInt();
        BlockPos pos = new BlockPos(x, y, z);

        final int fNumFloors = numFloors;
        final int fLayoutSeed = layoutSeed;
        return Optional.of(new GenerationStub(pos, builder -> {
            builder.addPiece(new DungeonPiece(pos, fNumFloors, fLayoutSeed));
        }));
    }

    private static final int FLOOR_SPACING = 7;
    private static final int ROOM_HEIGHT = 4;
    private static final int CORRIDOR_LEN = 6;
    private static final int SIDE_ROOM_HW = 3;

    private static final int[] DIR_X = {0, 0, 1, -1};
    private static final int[] DIR_Z = {-1, 1, 0, 0};

    public static class DungeonPiece extends StructurePiece {

        private final int baseY;
        private final int numFloors;
        private final int layoutSeed;

        public DungeonPiece(BlockPos pos, int numFloors, int layoutSeed) {
            super(GrandterrainStructurePieces.DUNGEON_PIECE, 0,
                    new BoundingBox(pos.getX() - 20, pos.getY() - (numFloors - 1) * FLOOR_SPACING - 2,
                            pos.getZ() - 20,
                            pos.getX() + 20, pos.getY() + ROOM_HEIGHT + 2, pos.getZ() + 20));
            this.baseY = pos.getY();
            this.numFloors = numFloors;
            this.layoutSeed = layoutSeed;
        }

        public DungeonPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(GrandterrainStructurePieces.DUNGEON_PIECE, tag);
            this.baseY = tag.getIntOr("baseY", 0);
            this.numFloors = tag.getIntOr("numFloors", 2);
            this.layoutSeed = tag.getIntOr("layoutSeed", 0);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("baseY", baseY);
            tag.putInt("numFloors", numFloors);
            tag.putInt("layoutSeed", layoutSeed);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager,
                                ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBB, ChunkPos chunkPos, BlockPos pivot) {
            int cx = (boundingBox.minX() + boundingBox.maxX()) / 2;
            int cz = (boundingBox.minZ() + boundingBox.maxZ()) / 2;

            for (int floor = 0; floor < numFloors; floor++) {
                int fy = baseY - floor * FLOOR_SPACING;
                generateFloor(level, random, cx, fy, cz, floor, chunkBB);

                if (floor < numFloors - 1) {
                    buildLadderShaft(level, cx + 3, fy, cz + 3, chunkBB);
                }
            }
        }

        private void generateFloor(WorldGenLevel level, RandomSource random,
                                    int cx, int fy, int cz, int floor, BoundingBox bb) {
            int halfW = centralRoomHalfW(floor);
            boolean isBossFloor = floor == numFloors - 1;

            carveRoom(level, cx, fy, cz, halfW, halfW, bb);

            for (int dir = 0; dir < 4; dir++) {
                if (!hasCorridor(floor, dir)) continue;

                int dx = DIR_X[dir];
                int dz = DIR_Z[dir];

                buildCorridor(level, cx, fy, cz, halfW, dx, dz, bb);

                int roomCX = cx + dx * (halfW + CORRIDOR_LEN + SIDE_ROOM_HW);
                int roomCZ = cz + dz * (halfW + CORRIDOR_LEN + SIDE_ROOM_HW);
                carveRoom(level, roomCX, fy, roomCZ, SIDE_ROOM_HW, SIDE_ROOM_HW, bb);

                int type = roomType(floor, dir);
                decorateSideRoom(level, random, roomCX, fy, roomCZ, SIDE_ROOM_HW, type, bb);
            }

            if (isBossFloor) {
                decorateBossRoom(level, random, cx, fy, cz, halfW, bb);
            } else {
                decorateCentralRoom(level, random, cx, fy, cz, halfW, floor, bb);
            }

            addAtmosphere(level, cx, fy, cz, halfW, bb);
        }

        private int centralRoomHalfW(int floor) {
            if (floor == numFloors - 1) return 6;
            return floor == 0 ? 5 : 4;
        }

        private boolean hasCorridor(int floor, int dir) {
            int skip1 = ((layoutSeed * 31 + floor * 73) & 0x7FFFFFFF) % 4;
            if (dir == skip1) return false;
            if (floor == numFloors - 1) {
                int skip2 = ((layoutSeed * 37 + floor * 59) & 0x7FFFFFFF) % 4;
                if (skip2 == skip1) skip2 = (skip2 + 1) % 4;
                if (dir == skip2) return false;
            }
            return true;
        }

        private int roomType(int floor, int dir) {
            int hash = ((layoutSeed * 41 + floor * 53 + dir * 67) & 0x7FFFFFFF);
            return hash % 5;
        }

        private void carveRoom(WorldGenLevel level, int cx, int fy, int cz,
                                int halfW, int halfH, BoundingBox bb) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                for (int dz = -halfH; dz <= halfH; dz++) {
                    setBlock(level, floorBlock(fy), cx + dx, fy, cz + dz, bb);
                    for (int h = 1; h <= ROOM_HEIGHT; h++) {
                        setBlock(level, Blocks.AIR.defaultBlockState(),
                                 cx + dx, fy + h, cz + dz, bb);
                    }
                    setBlock(level, ceilingBlock(fy), cx + dx, fy + ROOM_HEIGHT + 1, cz + dz, bb);
                }
            }
        }

        private void buildCorridor(WorldGenLevel level, int cx, int fy, int cz,
                                    int halfW, int dx, int dz, BoundingBox bb) {
            for (int step = 1; step <= CORRIDOR_LEN; step++) {
                int corrX = cx + dx * (halfW + step);
                int corrZ = cz + dz * (halfW + step);

                for (int w = -1; w <= 1; w++) {
                    int wx = corrX + (dx == 0 ? w : 0);
                    int wz = corrZ + (dz == 0 ? w : 0);

                    setBlock(level, floorBlock(fy), wx, fy, wz, bb);
                    for (int h = 1; h <= ROOM_HEIGHT; h++) {
                        setBlock(level, Blocks.AIR.defaultBlockState(),
                                 wx, fy + h, wz, bb);
                    }
                    setBlock(level, ceilingBlock(fy), wx, fy + ROOM_HEIGHT + 1, wz, bb);
                }

                if (step == CORRIDOR_LEN / 2) {
                    int torchX = corrX + (dx == 0 ? 2 : 0);
                    int torchZ = corrZ + (dz == 0 ? 2 : 0);
                    Direction torchFace = dx == 0 ? Direction.WEST : Direction.NORTH;
                    setBlock(level, ceilingBlock(fy), torchX, fy + 3, torchZ, bb);
                    setBlock(level, Blocks.WALL_TORCH.defaultBlockState()
                                    .setValue(WallTorchBlock.FACING, torchFace),
                             torchX - (dx == 0 ? 1 : 0), fy + 3,
                             torchZ - (dz == 0 ? 1 : 0), bb);
                }
            }
        }

        private void buildLadderShaft(WorldGenLevel level, int sx, int fy, int sz,
                                       BoundingBox bb) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    setBlock(level, Blocks.AIR.defaultBlockState(),
                             sx + dx, fy, sz + dz, bb);
                    for (int h = -FLOOR_SPACING + 1; h <= ROOM_HEIGHT; h++) {
                        setBlock(level, Blocks.AIR.defaultBlockState(),
                                 sx + dx, fy + h, sz + dz, bb);
                    }
                }
            }

            for (int h = -FLOOR_SPACING + 1; h <= ROOM_HEIGHT; h++) {
                setBlock(level, Blocks.LADDER.defaultBlockState()
                                .setValue(LadderBlock.FACING, Direction.WEST),
                         sx + 1, fy + h, sz, bb);
            }

            setBlock(level, ceilingBlock(fy),
                     sx, fy + ROOM_HEIGHT + 1, sz, bb);
            setBlock(level, ceilingBlock(fy),
                     sx + 1, fy + ROOM_HEIGHT + 1, sz, bb);
            setBlock(level, ceilingBlock(fy),
                     sx, fy + ROOM_HEIGHT + 1, sz + 1, bb);
            setBlock(level, ceilingBlock(fy),
                     sx + 1, fy + ROOM_HEIGHT + 1, sz + 1, bb);
        }

        private void decorateSideRoom(WorldGenLevel level, RandomSource random,
                                       int cx, int fy, int cz, int halfW,
                                       int type, BoundingBox bb) {
            switch (type) {
                case 0 -> decorateStorage(level, random, cx, fy, cz, halfW, bb);
                case 1 -> decorateLibrary(level, cx, fy, cz, halfW, bb);
                case 2 -> decorateSpawnerRoom(level, random, cx, fy, cz, halfW, bb);
                case 3 -> decorateCellBlock(level, cx, fy, cz, halfW, bb);
                case 4 -> decoratePitRoom(level, cx, fy, cz, halfW, bb);
            }
        }

        private void decorateStorage(WorldGenLevel level, RandomSource random,
                                      int cx, int fy, int cz, int halfW, BoundingBox bb) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                if (posHash(cx + dx, fy + 1, cz - halfW) % 3 != 0) {
                    setBlock(level, Blocks.BARREL.defaultBlockState(),
                             cx + dx, fy + 1, cz - halfW, bb);
                }
            }

            for (int dz = -halfW; dz <= halfW; dz++) {
                if (posHash(cx + halfW, fy + 1, cz + dz) % 4 == 0) {
                    setBlock(level, Blocks.BARREL.defaultBlockState(),
                             cx + halfW, fy + 1, cz + dz, bb);
                }
            }

            placeChest(level, cx - 1, fy + 1, cz, GrandterrainLootTables.DUNGEON_COMMON, bb);
            if (posHash(cx + 1, fy + 1, cz) % 3 == 0) {
                placeChest(level, cx + 1, fy + 1, cz, GrandterrainLootTables.DUNGEON_COMMON, bb);
            }
        }

        private void decorateLibrary(WorldGenLevel level, int cx, int fy, int cz,
                                      int halfW, BoundingBox bb) {
            for (int dx = -(halfW - 1); dx <= halfW - 1; dx++) {
                for (int h = 1; h <= 3; h++) {
                    setBlock(level, Blocks.BOOKSHELF.defaultBlockState(),
                             cx + dx, fy + h, cz - halfW, bb);
                }
            }

            for (int dz = -(halfW - 1); dz <= halfW - 1; dz++) {
                for (int h = 1; h <= 2; h++) {
                    setBlock(level, Blocks.BOOKSHELF.defaultBlockState(),
                             cx + halfW, fy + h, cz + dz, bb);
                }
            }

            setBlock(level, Blocks.LECTERN.defaultBlockState(),
                     cx, fy + 1, cz, bb);
            setBlock(level, Blocks.LANTERN.defaultBlockState(),
                     cx, fy + ROOM_HEIGHT, cz, bb);
        }

        private void decorateSpawnerRoom(WorldGenLevel level, RandomSource random,
                                          int cx, int fy, int cz, int halfW,
                                          BoundingBox bb) {
            placeSpawner(level, random, cx, fy + 1, cz, spawnerEntity(cx, fy, cz), bb);

            for (int dx = -(halfW - 1); dx <= halfW - 1; dx++) {
                for (int dz = -(halfW - 1); dz <= halfW - 1; dz++) {
                    if (posHash(cx + dx, fy + 3, cz + dz) % 4 == 0) {
                        setBlock(level, Blocks.COBWEB.defaultBlockState(),
                                 cx + dx, fy + 3, cz + dz, bb);
                    }
                }
            }

            for (int dx = -halfW; dx <= halfW; dx++) {
                for (int dz = -halfW; dz <= halfW; dz++) {
                    if ((Math.abs(dx) == halfW || Math.abs(dz) == halfW)
                            && posHash(cx + dx, fy + 1, cz + dz) % 5 == 0) {
                        setBlock(level, Blocks.COBWEB.defaultBlockState(),
                                 cx + dx, fy + 1, cz + dz, bb);
                    }
                }
            }

            if (posHash(cx - halfW + 1, fy + 1, cz) % 2 == 0) {
                placeChest(level, cx - halfW + 1, fy + 1, cz,
                           GrandterrainLootTables.DUNGEON_COMMON, bb);
            }
        }

        private void decorateCellBlock(WorldGenLevel level, int cx, int fy, int cz,
                                        int halfW, BoundingBox bb) {
            for (int h = 1; h <= ROOM_HEIGHT; h++) {
                for (int dz = -halfW; dz <= halfW; dz++) {
                    setBlock(level, Blocks.IRON_BARS.defaultBlockState(),
                             cx, fy + h, cz + dz, bb);
                }
            }
            for (int h = 1; h <= 3; h++) {
                setBlock(level, Blocks.AIR.defaultBlockState(), cx, fy + h, cz, bb);
            }

            for (int side = -1; side <= 1; side += 2) {
                int cellCX = cx + side * (halfW / 2 + 1);
                if (posHash(cellCX, fy + 1, cz) % 3 == 0) {
                    setBlock(level, Blocks.SKELETON_SKULL.defaultBlockState(),
                             cellCX, fy + 1, cz - halfW + 1, bb);
                }
                if (posHash(cellCX, fy + 2, cz) % 4 == 0) {
                    setBlock(level, Blocks.IRON_BARS.defaultBlockState(),
                             cellCX, fy + ROOM_HEIGHT, cz, bb);
                }
            }
        }

        private void decoratePitRoom(WorldGenLevel level, int cx, int fy, int cz,
                                      int halfW, BoundingBox bb) {
            for (int dx = -(halfW - 1); dx <= halfW - 1; dx++) {
                for (int dz = -(halfW - 1); dz <= halfW - 1; dz++) {
                    boolean isBridge = Math.abs(dx) <= 0;
                    if (!isBridge) {
                        setBlock(level, Blocks.AIR.defaultBlockState(),
                                 cx + dx, fy, cz + dz, bb);
                        setBlock(level, Blocks.AIR.defaultBlockState(),
                                 cx + dx, fy - 1, cz + dz, bb);
                        if (posHash(cx + dx, fy - 2, cz + dz) % 3 == 0) {
                            setBlock(level, Blocks.COBWEB.defaultBlockState(),
                                     cx + dx, fy - 1, cz + dz, bb);
                        }
                        setBlock(level, floorBlock(fy - 2), cx + dx, fy - 2, cz + dz, bb);
                    }
                }
            }
        }

        private void decorateCentralRoom(WorldGenLevel level, RandomSource random,
                                          int cx, int fy, int cz, int halfW,
                                          int floor, BoundingBox bb) {
            setBlock(level, Blocks.TORCH.defaultBlockState(),
                     cx - halfW + 1, fy + 1, cz - halfW + 1, bb);
            setBlock(level, Blocks.TORCH.defaultBlockState(),
                     cx + halfW - 1, fy + 1, cz - halfW + 1, bb);
            setBlock(level, Blocks.TORCH.defaultBlockState(),
                     cx - halfW + 1, fy + 1, cz + halfW - 1, bb);
            setBlock(level, Blocks.TORCH.defaultBlockState(),
                     cx + halfW - 1, fy + 1, cz + halfW - 1, bb);

            if (floor == 0) {
                for (int dx = -1; dx <= 1; dx++) {
                    setBlock(level, Blocks.OAK_PLANKS.defaultBlockState(),
                             cx + dx, fy + 1, cz, bb);
                }
                setBlock(level, Blocks.CRAFTING_TABLE.defaultBlockState(),
                         cx - halfW + 1, fy + 1, cz, bb);
            }
        }

        private void decorateBossRoom(WorldGenLevel level, RandomSource random,
                                       int cx, int fy, int cz, int halfW,
                                       BoundingBox bb) {
            setBlock(level, Blocks.REDSTONE_LAMP.defaultBlockState(),
                     cx - halfW + 1, fy + 1, cz - halfW + 1, bb);
            setBlock(level, Blocks.REDSTONE_LAMP.defaultBlockState(),
                     cx + halfW - 1, fy + 1, cz - halfW + 1, bb);
            setBlock(level, Blocks.REDSTONE_LAMP.defaultBlockState(),
                     cx - halfW + 1, fy + 1, cz + halfW - 1, bb);
            setBlock(level, Blocks.REDSTONE_LAMP.defaultBlockState(),
                     cx + halfW - 1, fy + 1, cz + halfW - 1, bb);

            setBlock(level, Blocks.REDSTONE_BLOCK.defaultBlockState(),
                     cx - halfW + 1, fy, cz - halfW + 1, bb);
            setBlock(level, Blocks.REDSTONE_BLOCK.defaultBlockState(),
                     cx + halfW - 1, fy, cz - halfW + 1, bb);
            setBlock(level, Blocks.REDSTONE_BLOCK.defaultBlockState(),
                     cx - halfW + 1, fy, cz + halfW - 1, bb);
            setBlock(level, Blocks.REDSTONE_BLOCK.defaultBlockState(),
                     cx + halfW - 1, fy, cz + halfW - 1, bb);

            placeSpawner(level, random, cx, fy + 1, cz, EntityType.CAVE_SPIDER, bb);

            placeChest(level, cx - 2, fy + 1, cz - halfW + 1,
                       GrandterrainLootTables.DUNGEON_BOSS, bb);
            placeChest(level, cx + 2, fy + 1, cz - halfW + 1,
                       GrandterrainLootTables.DUNGEON_BOSS, bb);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    setBlock(level, Blocks.SOUL_TORCH.defaultBlockState(),
                             cx + dx * 3, fy + 1, cz + dz * 3, bb);
                }
            }

            for (int h = 2; h <= ROOM_HEIGHT; h++) {
                setBlock(level, Blocks.IRON_BARS.defaultBlockState(), cx - 3, fy + h, cz, bb);
                setBlock(level, Blocks.IRON_BARS.defaultBlockState(), cx + 3, fy + h, cz, bb);
                setBlock(level, Blocks.IRON_BARS.defaultBlockState(), cx, fy + h, cz - 3, bb);
                setBlock(level, Blocks.IRON_BARS.defaultBlockState(), cx, fy + h, cz + 3, bb);
            }
        }

        private void addAtmosphere(WorldGenLevel level, int cx, int fy, int cz,
                                    int halfW, BoundingBox bb) {
            for (int dx = -(halfW - 1); dx <= halfW - 1; dx++) {
                for (int dz = -(halfW - 1); dz <= halfW - 1; dz++) {
                    if (posHash(cx + dx, fy + ROOM_HEIGHT, cz + dz) % 12 == 0) {
                        setBlock(level, Blocks.COBWEB.defaultBlockState(),
                                 cx + dx, fy + ROOM_HEIGHT, cz + dz, bb);
                    }
                }
            }
        }

        private EntityType<?> spawnerEntity(int x, int y, int z) {
            int hash = posHash(x, y, z);
            return switch (hash % 4) {
                case 0 -> EntityType.ZOMBIE;
                case 1 -> EntityType.SKELETON;
                case 2 -> EntityType.SPIDER;
                default -> EntityType.CAVE_SPIDER;
            };
        }

        private BlockState floorBlock(int y) {
            if (y >= 200) {
                return Blocks.COBBLESTONE.defaultBlockState();
            }
            if (y >= 100) {
                return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            }
            return Blocks.DEEPSLATE_TILES.defaultBlockState();
        }

        private BlockState ceilingBlock(int y) {
            if (y >= 200) {
                return Blocks.STONE_BRICKS.defaultBlockState();
            }
            if (y >= 100) {
                return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            }
            return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        }

        private int posHash(int x, int y, int z) {
            return ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 0x7FFFFFFF;
        }

        private void placeChest(WorldGenLevel level, int x, int y, int z,
                                 ResourceKey<LootTable> lootTable, BoundingBox bb) {
            BlockPos pos = new BlockPos(x, y, z);
            if (bb.isInside(pos)) {
                level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
                if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
                    container.setLootTable(lootTable, pos.asLong());
                }
            }
        }

        private void placeSpawner(WorldGenLevel level, RandomSource random,
                                   int x, int y, int z, EntityType<?> entity,
                                   BoundingBox bb) {
            BlockPos pos = new BlockPos(x, y, z);
            if (bb.isInside(pos)) {
                level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 2);
                if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity sbe) {
                    sbe.setEntityId(entity, RandomSource.create(posHash(x, y, z)));
                }
            }
        }

        private void setBlock(WorldGenLevel level, BlockState state,
                               int x, int y, int z, BoundingBox bb) {
            BlockPos pos = new BlockPos(x, y, z);
            if (bb.isInside(pos)) {
                level.setBlock(pos, state, 2);
            }
        }
    }
}
