package com.grandterrain.worldgen.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.Optional;

/**
 * Underground dungeon with interconnected rooms, corridors, and spawner chambers.
 */
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
        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        int surfaceY = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());

        // Place underground, at least 20 blocks below surface
        int y = Math.max(context.heightAccessor().getMinY() + 10, surfaceY - 30 - context.random().nextInt(40));

        BlockPos pos = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(pos, builder -> {
            builder.addPiece(new DungeonPiece(pos, context.random()));
        }));
    }

    public static class DungeonPiece extends StructurePiece {

        private final int baseY;

        public DungeonPiece(BlockPos pos, RandomSource random) {
            super(GrandterrainStructurePieces.DUNGEON_PIECE, 0,
                    new BoundingBox(pos.getX() - 15, pos.getY() - 1, pos.getZ() - 15,
                            pos.getX() + 15, pos.getY() + 6, pos.getZ() + 15));
            this.baseY = pos.getY();
        }

        public DungeonPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(GrandterrainStructurePieces.DUNGEON_PIECE, tag);
            this.baseY = tag.getIntOr("baseY", 0);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("baseY", baseY);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager,
                                ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBB, ChunkPos chunkPos, BlockPos pivot) {
            int cx = (boundingBox.minX() + boundingBox.maxX()) / 2;
            int cz = (boundingBox.minZ() + boundingBox.maxZ()) / 2;
            int y = baseY;

            BlockState floor = Blocks.COBBLESTONE.defaultBlockState();
            BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();

            // Main room (8x8)
            carveRoom(level, cx, y, cz, 4, 4, floor, wall, chunkBB);

            // Corridors in cardinal directions
            int[][] corridorDirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] dir : corridorDirs) {
                if (random.nextInt(3) == 0) continue; // Skip some corridors

                // Corridor (3 blocks wide, 10 long)
                for (int step = 5; step <= 10; step++) {
                    int rx = cx + dir[0] * step;
                    int rz = cz + dir[1] * step;
                    for (int h = 0; h <= 3; h++) {
                        for (int w = -1; w <= 1; w++) {
                            int wx = rx + (dir[1] != 0 ? w : 0);
                            int wz = rz + (dir[0] != 0 ? w : 0);
                            setBlock(level, Blocks.AIR.defaultBlockState(), wx, y + h, wz, chunkBB);
                        }
                    }
                    // Floor
                    for (int w = -1; w <= 1; w++) {
                        int wx = rx + (dir[1] != 0 ? w : 0);
                        int wz = rz + (dir[0] != 0 ? w : 0);
                        setBlock(level, floor, wx, y - 1, wz, chunkBB);
                    }
                }

                // Small room at end of corridor
                int roomX = cx + dir[0] * 11;
                int roomZ = cz + dir[1] * 11;
                carveRoom(level, roomX, y, roomZ, 3, 3, floor, wall, chunkBB);

                // Spawner in some rooms
                if (random.nextInt(2) == 0) {
                    setBlock(level, Blocks.SPAWNER.defaultBlockState(), roomX, y, roomZ, chunkBB);
                }

                // Chest in some rooms
                if (random.nextInt(2) == 0) {
                    int chestX = roomX + (random.nextInt(3) - 1);
                    int chestZ = roomZ + (random.nextInt(3) - 1);
                    setBlock(level, Blocks.CHEST.defaultBlockState(), chestX, y, chestZ, chunkBB);
                }
            }

            // Torches in main room
            // Place torches on the floor (y is the air layer, y-1 is the cobblestone floor)
            setBlock(level, Blocks.TORCH.defaultBlockState(), cx - 3, y, cz, chunkBB);
            setBlock(level, Blocks.TORCH.defaultBlockState(), cx + 3, y, cz, chunkBB);
            setBlock(level, Blocks.TORCH.defaultBlockState(), cx, y, cz - 3, chunkBB);
            setBlock(level, Blocks.TORCH.defaultBlockState(), cx, y, cz + 3, chunkBB);
        }

        private void carveRoom(WorldGenLevel level, int cx, int y, int cz,
                                int halfW, int halfH, BlockState floor, BlockState wall,
                                BoundingBox bb) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                for (int dz = -halfH; dz <= halfH; dz++) {
                    // Floor
                    setBlock(level, floor, cx + dx, y - 1, cz + dz, bb);
                    // Air space
                    for (int h = 0; h <= 4; h++) {
                        setBlock(level, Blocks.AIR.defaultBlockState(), cx + dx, y + h, cz + dz, bb);
                    }
                    // Ceiling
                    setBlock(level, wall, cx + dx, y + 5, cz + dz, bb);
                }
            }
        }

        private void setBlock(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox bb) {
            BlockPos pos = new BlockPos(x, y, z);
            if (bb.isInside(pos)) {
                level.setBlock(pos, state, 2);
            }
        }
    }
}
