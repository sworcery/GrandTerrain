package com.grandterrain.worldgen.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Optional;

public class WatchtowerStructure extends Structure {

    public static final MapCodec<WatchtowerStructure> CODEC = simpleCodec(WatchtowerStructure::new);

    public WatchtowerStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return GrandterrainStructures.WATCHTOWER;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (context.chunkGenerator() instanceof com.grandterrain.worldgen.GrandterrainChunkGenerator gen
                && !gen.getConfigSnapshot().enableWatchtowers()) {
            return Optional.empty();
        }

        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        int y = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());

        if (y < 135 || y > 500) return Optional.empty();

        var hm = Heightmap.Types.WORLD_SURFACE_WG;
        int yN = context.chunkGenerator().getBaseHeight(x, z - 5, hm, context.heightAccessor(), context.randomState());
        int yS = context.chunkGenerator().getBaseHeight(x, z + 5, hm, context.heightAccessor(), context.randomState());
        int yE = context.chunkGenerator().getBaseHeight(x + 5, z, hm, context.heightAccessor(), context.randomState());
        int yW = context.chunkGenerator().getBaseHeight(x - 5, z, hm, context.heightAccessor(), context.randomState());
        int maxDelta = Math.max(Math.max(Math.abs(y - yN), Math.abs(y - yS)),
                Math.max(Math.abs(y - yE), Math.abs(y - yW)));
        if (maxDelta > 6) return Optional.empty();

        BlockPos pos = new BlockPos(x, y, z);
        int towerHeight = 60 + context.random().nextInt(21);

        return Optional.of(new GenerationStub(pos, builder -> {
            builder.addPiece(new WatchtowerPiece(pos, towerHeight));
        }));
    }

    public static class WatchtowerPiece extends StructurePiece {

        private static final int HALF_BASE = 3;
        private static final int FLOOR_HEIGHT = 6;

        private final int baseY;
        private final int towerHeight;

        public WatchtowerPiece(BlockPos pos, int towerHeight) {
            super(GrandterrainStructurePieces.WATCHTOWER_PIECE, 0,
                    new BoundingBox(pos.getX() - 5, pos.getY() - 2, pos.getZ() - 5,
                            pos.getX() + 5, pos.getY() + towerHeight + 10, pos.getZ() + 5));
            this.baseY = pos.getY();
            this.towerHeight = towerHeight;
        }

        public WatchtowerPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(GrandterrainStructurePieces.WATCHTOWER_PIECE, tag);
            this.baseY = tag.getIntOr("baseY", 64);
            this.towerHeight = tag.getIntOr("towerHeight", 70);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("baseY", baseY);
            tag.putInt("towerHeight", towerHeight);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager,
                                ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBB, ChunkPos chunkPos, BlockPos pivot) {
            int cx = (boundingBox.minX() + boundingBox.maxX()) / 2;
            int cz = (boundingBox.minZ() + boundingBox.maxZ()) / 2;
            int y = baseY;

            int shaftHeight = towerHeight - 5;
            int lookoutY = y + shaftHeight;

            buildBase(level, cx, y, cz, chunkBB);
            buildShaft(level, random, cx, y, cz, shaftHeight, chunkBB);
            buildLookout(level, random, cx, lookoutY, cz, chunkBB);
            buildRoof(level, cx, lookoutY + 4, cz, chunkBB);
            buildInterior(level, random, cx, y, cz, shaftHeight, chunkBB);

            // Door opening on south face
            for (int h = 1; h <= 3; h++) {
                setBlock(level, Blocks.AIR.defaultBlockState(),
                         cx, y + h, cz + HALF_BASE, chunkBB);
            }
        }

        private void buildBase(WorldGenLevel level, int cx, int y, int cz, BoundingBox bb) {
            BlockState deepslate = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            for (int dx = -HALF_BASE - 1; dx <= HALF_BASE + 1; dx++) {
                for (int dz = -HALF_BASE - 1; dz <= HALF_BASE + 1; dz++) {
                    for (int dy = -2; dy <= 0; dy++) {
                        setBlock(level, deepslate, cx + dx, y + dy, cz + dz, bb);
                    }
                }
            }
        }

        private void buildShaft(WorldGenLevel level, RandomSource random,
                                 int cx, int y, int cz, int height, BoundingBox bb) {
            for (int h = 1; h <= height; h++) {
                for (int dx = -HALF_BASE; dx <= HALF_BASE; dx++) {
                    for (int dz = -HALF_BASE; dz <= HALF_BASE; dz++) {
                        boolean isEdge = Math.abs(dx) == HALF_BASE || Math.abs(dz) == HALF_BASE;
                        if (isEdge) {
                            boolean isWindow = (h % 8 == 5) &&
                                    ((dx == 0 && Math.abs(dz) == HALF_BASE) ||
                                     (dz == 0 && Math.abs(dx) == HALF_BASE));
                            if (isWindow) {
                                setBlock(level, Blocks.IRON_BARS.defaultBlockState(),
                                         cx + dx, y + h, cz + dz, bb);
                            } else {
                                setBlock(level, weatheredStone(random),
                                         cx + dx, y + h, cz + dz, bb);
                            }
                        } else {
                            setBlock(level, Blocks.AIR.defaultBlockState(),
                                     cx + dx, y + h, cz + dz, bb);
                        }
                    }
                }
            }
        }

        private void buildLookout(WorldGenLevel level, RandomSource random,
                                   int cx, int lookoutY, int cz, BoundingBox bb) {
            int platHW = HALF_BASE + 1;
            BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();

            // Floor platform (extends 1 block beyond shaft)
            for (int dx = -platHW; dx <= platHW; dx++) {
                for (int dz = -platHW; dz <= platHW; dz++) {
                    setBlock(level, stone, cx + dx, lookoutY, cz + dz, bb);
                }
            }

            // Short walls with battlements
            for (int h = 1; h <= 3; h++) {
                for (int i = -platHW; i <= platHW; i++) {
                    setBlock(level, stone, cx - platHW, lookoutY + h, cz + i, bb);
                    setBlock(level, stone, cx + platHW, lookoutY + h, cz + i, bb);
                    setBlock(level, stone, cx + i, lookoutY + h, cz - platHW, bb);
                    setBlock(level, stone, cx + i, lookoutY + h, cz + platHW, bb);
                }
            }

            // Battlements
            for (int i = -platHW; i <= platHW; i += 2) {
                setBlock(level, stone, cx - platHW, lookoutY + 4, cz + i, bb);
                setBlock(level, stone, cx + platHW, lookoutY + 4, cz + i, bb);
                setBlock(level, stone, cx + i, lookoutY + 4, cz - platHW, bb);
                setBlock(level, stone, cx + i, lookoutY + 4, cz + platHW, bb);
            }

            // Clear interior air
            for (int h = 1; h <= 3; h++) {
                for (int dx = -(platHW - 1); dx <= platHW - 1; dx++) {
                    for (int dz = -(platHW - 1); dz <= platHW - 1; dz++) {
                        setBlock(level, Blocks.AIR.defaultBlockState(),
                                 cx + dx, lookoutY + h, cz + dz, bb);
                    }
                }
            }

            // Lanterns on corners
            setBlock(level, Blocks.LANTERN.defaultBlockState(), cx - platHW, lookoutY + 4, cz - platHW, bb);
            setBlock(level, Blocks.LANTERN.defaultBlockState(), cx + platHW, lookoutY + 4, cz - platHW, bb);
            setBlock(level, Blocks.LANTERN.defaultBlockState(), cx - platHW, lookoutY + 4, cz + platHW, bb);
            setBlock(level, Blocks.LANTERN.defaultBlockState(), cx + platHW, lookoutY + 4, cz + platHW, bb);
        }

        private void buildRoof(WorldGenLevel level, int cx, int roofY, int cz, BoundingBox bb) {
            int platHW = HALF_BASE + 1;
            for (int layer = 0; layer <= platHW + 1; layer++) {
                int w = platHW + 1 - layer;
                for (int dx = -w; dx <= w; dx++) {
                    for (int dz = -w; dz <= w; dz++) {
                        setBlock(level, Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                                 cx + dx, roofY + 1 + layer, cz + dz, bb);
                    }
                }
            }
            setBlock(level, Blocks.SEA_LANTERN.defaultBlockState(),
                     cx, roofY + platHW + 3, cz, bb);
        }

        private void buildInterior(WorldGenLevel level, RandomSource random,
                                    int cx, int y, int cz, int shaftH, BoundingBox bb) {
            int inner = HALF_BASE - 1;

            // Floors
            for (int h = 1; h < shaftH; h += FLOOR_HEIGHT) {
                for (int dx = -inner; dx <= inner; dx++) {
                    for (int dz = -inner; dz <= inner; dz++) {
                        setBlock(level, Blocks.OAK_PLANKS.defaultBlockState(),
                                 cx + dx, y + h, cz + dz, bb);
                    }
                }
            }

            // Ladder on north wall
            for (int h = 2; h <= shaftH; h++) {
                setBlock(level, Blocks.LADDER.defaultBlockState()
                                .setValue(LadderBlock.FACING, Direction.SOUTH),
                         cx, y + h, cz - inner, bb);
            }

            // Wall torches
            for (int h = 4; h < shaftH; h += FLOOR_HEIGHT) {
                setBlock(level, Blocks.WALL_TORCH.defaultBlockState()
                                .setValue(WallTorchBlock.FACING, Direction.NORTH),
                         cx, y + h, cz + inner, bb);
            }

            // Chest near top
            int topFloor = ((shaftH - 1) / FLOOR_HEIGHT) * FLOOR_HEIGHT + 1;
            placeChest(level, cx - inner, y + topFloor + 1, cz, bb);
        }

        private void placeChest(WorldGenLevel level, int x, int y, int z, BoundingBox bb) {
            BlockPos pos = new BlockPos(x, y, z);
            if (bb.isInside(pos)) {
                level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
                if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
                    container.setLootTable(GrandterrainLootTables.WATCHTOWER_SUPPLY, pos.asLong());
                }
            }
        }

        private BlockState weatheredStone(RandomSource random) {
            return switch (random.nextInt(10)) {
                case 0 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                case 1 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                default -> Blocks.STONE_BRICKS.defaultBlockState();
            };
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
