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

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Optional;

public class CastleStructure extends Structure {

    public static final MapCodec<CastleStructure> CODEC = simpleCodec(CastleStructure::new);

    public CastleStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return GrandterrainStructures.CASTLE;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (context.chunkGenerator() instanceof com.grandterrain.worldgen.GrandterrainChunkGenerator gen
                && !gen.getConfigSnapshot().enableCastles()) {
            return Optional.empty();
        }

        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        var hm = Heightmap.Types.WORLD_SURFACE_WG;
        int y = context.chunkGenerator().getBaseHeight(x, z, hm,
                context.heightAccessor(), context.randomState());

        if (y < 135 || y > 350) return Optional.empty();

        int yN = context.chunkGenerator().getBaseHeight(x, z - 20, hm, context.heightAccessor(), context.randomState());
        int yS = context.chunkGenerator().getBaseHeight(x, z + 20, hm, context.heightAccessor(), context.randomState());
        int yE = context.chunkGenerator().getBaseHeight(x + 20, z, hm, context.heightAccessor(), context.randomState());
        int yW = context.chunkGenerator().getBaseHeight(x - 20, z, hm, context.heightAccessor(), context.randomState());
        int maxDelta = Math.max(Math.max(Math.abs(y - yN), Math.abs(y - yS)),
                Math.max(Math.abs(y - yE), Math.abs(y - yW)));
        if (maxDelta > 10) return Optional.empty();

        BlockPos pos = new BlockPos(x, y, z);
        int totalHeight = 120 + context.random().nextInt(81);

        return Optional.of(new GenerationStub(pos, builder -> {
            builder.addPiece(new CastlePiece(pos, totalHeight));
        }));
    }

    public static class CastlePiece extends StructurePiece {

        private static final int WALL_HALF = 18;
        private static final int WALL_HEIGHT = 20;
        private static final int FLOOR_HEIGHT = 6;

        private final int baseY;
        private final int totalHeight;

        public CastlePiece(BlockPos pos, int totalHeight) {
            super(GrandterrainStructurePieces.CASTLE_PIECE, 0,
                    new BoundingBox(pos.getX() - 22, pos.getY() - 3, pos.getZ() - 22,
                            pos.getX() + 22, pos.getY() + totalHeight + 10, pos.getZ() + 22));
            this.baseY = pos.getY();
            this.totalHeight = totalHeight;
        }

        public CastlePiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(GrandterrainStructurePieces.CASTLE_PIECE, tag);
            this.baseY = tag.getIntOr("baseY", 64);
            this.totalHeight = tag.getIntOr("totalHeight", 150);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("baseY", baseY);
            tag.putInt("totalHeight", totalHeight);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager,
                                ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBB, ChunkPos chunkPos, BlockPos pivot) {
            int cx = (boundingBox.minX() + boundingBox.maxX()) / 2;
            int cz = (boundingBox.minZ() + boundingBox.maxZ()) / 2;
            int y = baseY;

            int towerH = totalHeight / 3;
            int keepH1 = totalHeight / 4;
            int keepH2 = totalHeight / 2;
            int keepH3 = (totalHeight * 7) / 10;

            buildFoundation(level, cx, y, cz, chunkBB);
            buildOuterWalls(level, random, cx, y, cz, chunkBB);
            buildCornerTowers(level, random, cx, y, cz, towerH, chunkBB);
            buildGatehouse(level, random, cx, y, cz, chunkBB);
            buildCourtyard(level, random, cx, y, cz, chunkBB);
            buildKeep(level, random, cx, y, cz, keepH1, keepH2, keepH3, chunkBB);
            buildSpire(level, random, cx, y + keepH3, cz, totalHeight - keepH3, chunkBB);
            buildInterior(level, random, cx, y, cz, keepH1, keepH2, keepH3, chunkBB);
        }

        private void buildFoundation(WorldGenLevel level, int cx, int y, int cz, BoundingBox bb) {
            BlockState deepslate = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
            for (int dx = -(WALL_HALF + 1); dx <= WALL_HALF + 1; dx++) {
                for (int dz = -(WALL_HALF + 1); dz <= WALL_HALF + 1; dz++) {
                    for (int dy = -3; dy <= 0; dy++) {
                        setBlock(level, deepslate, cx + dx, y + dy, cz + dz, bb);
                    }
                }
            }
        }

        private void buildOuterWalls(WorldGenLevel level, RandomSource random,
                                      int cx, int y, int cz, BoundingBox bb) {
            for (int i = -WALL_HALF; i <= WALL_HALF; i++) {
                for (int h = 1; h <= WALL_HEIGHT; h++) {
                    for (int t = 0; t <= 1; t++) {
                        BlockState wall = weatheredStone(random);
                        setBlock(level, wall, cx - WALL_HALF + t, y + h, cz + i, bb);
                        setBlock(level, wall, cx + WALL_HALF - t, y + h, cz + i, bb);
                        setBlock(level, wall, cx + i, y + h, cz - WALL_HALF + t, bb);
                        setBlock(level, wall, cx + i, y + h, cz + WALL_HALF - t, bb);
                    }
                }
            }
            for (int i = -WALL_HALF; i <= WALL_HALF; i += 2) {
                BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
                setBlock(level, stone, cx - WALL_HALF, y + WALL_HEIGHT + 1, cz + i, bb);
                setBlock(level, stone, cx + WALL_HALF, y + WALL_HEIGHT + 1, cz + i, bb);
                setBlock(level, stone, cx + i, y + WALL_HEIGHT + 1, cz - WALL_HALF, bb);
                setBlock(level, stone, cx + i, y + WALL_HEIGHT + 1, cz + WALL_HALF, bb);
            }
            BlockState slab = Blocks.STONE_BRICK_SLAB.defaultBlockState();
            for (int i = -(WALL_HALF - 2); i <= WALL_HALF - 2; i++) {
                setBlock(level, slab, cx - WALL_HALF + 2, y + WALL_HEIGHT, cz + i, bb);
                setBlock(level, slab, cx + WALL_HALF - 2, y + WALL_HEIGHT, cz + i, bb);
                setBlock(level, slab, cx + i, y + WALL_HEIGHT, cz - WALL_HALF + 2, bb);
                setBlock(level, slab, cx + i, y + WALL_HEIGHT, cz + WALL_HALF - 2, bb);
            }
        }

        private void buildCornerTowers(WorldGenLevel level, RandomSource random,
                                        int cx, int y, int cz, int towerH, BoundingBox bb) {
            int[][] corners = {{-WALL_HALF, -WALL_HALF}, {-WALL_HALF, WALL_HALF},
                               {WALL_HALF, -WALL_HALF}, {WALL_HALF, WALL_HALF}};
            for (int[] corner : corners) {
                buildTower(level, random, cx + corner[0], y, cz + corner[1], 3, towerH, bb);
            }
        }

        private void buildTower(WorldGenLevel level, RandomSource random,
                                 int tx, int y, int tz, int halfW, int height, BoundingBox bb) {
            for (int h = 1; h <= height; h++) {
                for (int dx = -halfW; dx <= halfW; dx++) {
                    for (int dz = -halfW; dz <= halfW; dz++) {
                        boolean isEdge = Math.abs(dx) == halfW || Math.abs(dz) == halfW;
                        if (isEdge) {
                            boolean isArrowSlit = (h % 8 == 5) &&
                                    ((dx == 0 && Math.abs(dz) == halfW) ||
                                     (dz == 0 && Math.abs(dx) == halfW));
                            if (isArrowSlit) {
                                setBlock(level, Blocks.IRON_BARS.defaultBlockState(),
                                         tx + dx, y + h, tz + dz, bb);
                            } else {
                                setBlock(level, weatheredStone(random), tx + dx, y + h, tz + dz, bb);
                            }
                        } else {
                            setBlock(level, Blocks.AIR.defaultBlockState(), tx + dx, y + h, tz + dz, bb);
                        }
                    }
                }
            }
            for (int layer = 0; layer <= halfW + 1; layer++) {
                int w = halfW + 1 - layer;
                for (int dx = -w; dx <= w; dx++) {
                    for (int dz = -w; dz <= w; dz++) {
                        setBlock(level, Blocks.DARK_OAK_PLANKS.defaultBlockState(),
                                 tx + dx, y + height + 1 + layer, tz + dz, bb);
                    }
                }
            }
            setBlock(level, Blocks.SEA_LANTERN.defaultBlockState(),
                     tx, y + height + halfW + 3, tz, bb);
        }

        private void buildGatehouse(WorldGenLevel level, RandomSource random,
                                     int cx, int y, int cz, BoundingBox bb) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int h = 1; h <= 5; h++) {
                    setBlock(level, Blocks.AIR.defaultBlockState(),
                             cx + dx, y + h, cz + WALL_HALF, bb);
                    setBlock(level, Blocks.AIR.defaultBlockState(),
                             cx + dx, y + h, cz + WALL_HALF - 1, bb);
                }
            }
            setBlock(level, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(),
                     cx, y + 6, cz + WALL_HALF, bb);
            setBlock(level, Blocks.STONE_BRICKS.defaultBlockState(),
                     cx - 1, y + 6, cz + WALL_HALF, bb);
            setBlock(level, Blocks.STONE_BRICKS.defaultBlockState(),
                     cx + 1, y + 6, cz + WALL_HALF, bb);
            for (int dx = -1; dx <= 1; dx++) {
                setBlock(level, Blocks.IRON_BARS.defaultBlockState(),
                         cx + dx, y + 4, cz + WALL_HALF, bb);
                setBlock(level, Blocks.IRON_BARS.defaultBlockState(),
                         cx + dx, y + 5, cz + WALL_HALF, bb);
            }
            for (int side = -1; side <= 1; side += 2) {
                int tx = cx + side * 4;
                for (int h = 1; h <= WALL_HEIGHT + 8; h++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        boolean isEdge = Math.abs(dx) == 1;
                        setBlock(level, weatheredStone(random),
                                 tx + dx, y + h, cz + WALL_HALF, bb);
                        if (isEdge) {
                            setBlock(level, weatheredStone(random),
                                     tx + dx, y + h, cz + WALL_HALF + 1, bb);
                        }
                    }
                }
                setBlock(level, Blocks.SEA_LANTERN.defaultBlockState(),
                         tx, y + WALL_HEIGHT + 9, cz + WALL_HALF, bb);
            }
        }

        private void buildCourtyard(WorldGenLevel level, RandomSource random,
                                     int cx, int y, int cz, BoundingBox bb) {
            for (int dx = -(WALL_HALF - 2); dx <= WALL_HALF - 2; dx++) {
                for (int dz = -(WALL_HALF - 2); dz <= WALL_HALF - 2; dz++) {
                    if (Math.abs(dx) <= 8 && Math.abs(dz) <= 8) continue;
                    BlockState floor = random.nextInt(3) == 0 ?
                            Blocks.COBBLESTONE.defaultBlockState() :
                            Blocks.STONE_BRICKS.defaultBlockState();
                    setBlock(level, floor, cx + dx, y, cz + dz, bb);
                }
            }
            for (int dz = 9; dz <= WALL_HALF - 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    setBlock(level, Blocks.STONE_BRICKS.defaultBlockState(),
                             cx + dx, y, cz + dz, bb);
                }
            }
            // Well in courtyard (stone walls at y and y+1 for containment)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        setBlock(level, Blocks.WATER.defaultBlockState(),
                                 cx + 10, y, cz, bb);
                    } else {
                        setBlock(level, Blocks.STONE_BRICKS.defaultBlockState(),
                                 cx + 10 + dx, y, cz + dz, bb);
                        setBlock(level, Blocks.STONE_BRICKS.defaultBlockState(),
                                 cx + 10 + dx, y + 1, cz + dz, bb);
                    }
                }
            }
        }

        private void buildKeep(WorldGenLevel level, RandomSource random,
                                int cx, int y, int cz,
                                int h1, int h2, int h3, BoundingBox bb) {
            buildKeepWalls(level, random, cx, y, cz, 1, h1, 8, bb);
            buildKeepLedge(level, cx, y + h1, cz, 8, 6, bb);

            buildKeepWalls(level, random, cx, y, cz, h1, h2, 6, bb);
            buildKeepLedge(level, cx, y + h2, cz, 6, 4, bb);

            buildKeepWalls(level, random, cx, y, cz, h2, h3, 4, bb);
            buildKeepLedge(level, cx, y + h3, cz, 4, 3, bb);

            for (int dx = -1; dx <= 1; dx++) {
                for (int h = 1; h <= 4; h++) {
                    setBlock(level, Blocks.AIR.defaultBlockState(), cx + dx, y + h, cz + 8, bb);
                }
            }
        }

        private void buildKeepWalls(WorldGenLevel level, RandomSource random,
                                     int cx, int y, int cz,
                                     int startH, int endH, int halfW, BoundingBox bb) {
            for (int h = startH; h <= endH; h++) {
                for (int dx = -halfW; dx <= halfW; dx++) {
                    for (int dz = -halfW; dz <= halfW; dz++) {
                        boolean isWall = Math.abs(dx) == halfW || Math.abs(dz) == halfW;
                        if (isWall) {
                            boolean isWindow = (h % FLOOR_HEIGHT == 4 || h % FLOOR_HEIGHT == 5)
                                    && h > startH + 3
                                    && ((Math.abs(dx) == halfW && dz == 0) ||
                                        (Math.abs(dz) == halfW && dx == 0));
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

        private void buildKeepLedge(WorldGenLevel level, int cx, int ledgeY, int cz,
                                     int outerHW, int innerHW, BoundingBox bb) {
            for (int dx = -outerHW; dx <= outerHW; dx++) {
                for (int dz = -outerHW; dz <= outerHW; dz++) {
                    if (Math.abs(dx) <= innerHW && Math.abs(dz) <= innerHW) continue;
                    setBlock(level, Blocks.STONE_BRICKS.defaultBlockState(),
                             cx + dx, ledgeY, cz + dz, bb);
                    if ((Math.abs(dx) == outerHW || Math.abs(dz) == outerHW)
                            && ((dx + dz) & 1) == 0) {
                        setBlock(level, Blocks.STONE_BRICKS.defaultBlockState(),
                                 cx + dx, ledgeY + 1, cz + dz, bb);
                    }
                }
            }
        }

        private void buildSpire(WorldGenLevel level, RandomSource random,
                                 int cx, int spireBaseY, int cz, int spireH, BoundingBox bb) {
            for (int h = 0; h < spireH; h++) {
                double progress = (double) h / spireH;
                int halfW = Math.max(0, (int) Math.round(3.0 * (1.0 - progress)));

                if (halfW == 0) {
                    setBlock(level, Blocks.STONE_BRICKS.defaultBlockState(),
                             cx, spireBaseY + h, cz, bb);
                } else {
                    for (int dx = -halfW; dx <= halfW; dx++) {
                        for (int dz = -halfW; dz <= halfW; dz++) {
                            boolean isEdge = Math.abs(dx) == halfW || Math.abs(dz) == halfW;
                            if (isEdge) {
                                BlockState block = (random.nextInt(5) == 0)
                                        ? Blocks.DARK_OAK_PLANKS.defaultBlockState()
                                        : Blocks.STONE_BRICKS.defaultBlockState();
                                setBlock(level, block, cx + dx, spireBaseY + h, cz + dz, bb);
                            }
                        }
                    }
                }
            }
            setBlock(level, Blocks.SEA_LANTERN.defaultBlockState(),
                     cx, spireBaseY + spireH, cz, bb);
            setBlock(level, Blocks.GOLD_BLOCK.defaultBlockState(),
                     cx, spireBaseY + spireH + 1, cz, bb);
        }

        private void buildInterior(WorldGenLevel level, RandomSource random,
                                    int cx, int y, int cz,
                                    int h1, int h2, int h3, BoundingBox bb) {
            int[] sectionEnds = {h1, h2, h3};
            int[] halfWidths = {8, 6, 4};

            for (int s = 0; s < 3; s++) {
                int startH = (s == 0) ? 1 : sectionEnds[s - 1];
                int endH = sectionEnds[s];
                int hw = halfWidths[s];
                int inner = hw - 1;

                for (int h = startH; h < endH; h += FLOOR_HEIGHT) {
                    for (int dx = -inner; dx <= inner; dx++) {
                        for (int dz = -inner; dz <= inner; dz++) {
                            setBlock(level, Blocks.OAK_PLANKS.defaultBlockState(),
                                     cx + dx, y + h, cz + dz, bb);
                        }
                    }
                }

                for (int h = startH + 1; h <= endH; h++) {
                    setBlock(level, Blocks.LADDER.defaultBlockState()
                                    .setValue(LadderBlock.FACING, Direction.SOUTH),
                             cx + inner - 1, y + h, cz - inner, bb);
                }

                for (int h = startH + 3; h < endH; h += FLOOR_HEIGHT) {
                    setBlock(level, Blocks.WALL_TORCH.defaultBlockState()
                                    .setValue(WallTorchBlock.FACING, Direction.SOUTH),
                             cx, y + h, cz - inner, bb);
                    setBlock(level, Blocks.WALL_TORCH.defaultBlockState()
                                    .setValue(WallTorchBlock.FACING, Direction.NORTH),
                             cx, y + h, cz + inner, bb);
                }

                for (int h = startH + FLOOR_HEIGHT * 2; h < endH; h += FLOOR_HEIGHT * 3) {
                    placeChest(level, cx - inner + 1, y + h + 1, cz, bb);
                }
            }

            // Ground floor: fireplace hearth
            for (int dx = -1; dx <= 1; dx++) {
                setBlock(level, Blocks.COBBLESTONE.defaultBlockState(),
                         cx + dx, y + 1, cz - 6, bb);
            }
            setBlock(level, Blocks.CAMPFIRE.defaultBlockState(), cx, y + 2, cz - 6, bb);

            // Ground floor: dining table
            for (int dx = -2; dx <= 2; dx++) {
                setBlock(level, Blocks.OAK_FENCE.defaultBlockState(),
                         cx + dx, y + 2, cz + 2, bb);
                setBlock(level, Blocks.OAK_PRESSURE_PLATE.defaultBlockState(),
                         cx + dx, y + 3, cz + 2, bb);
            }

            // Spawner in basement (below keep)
            placeSpawner(level, random, cx, y - 1, cz, bb);
        }

        private void placeChest(WorldGenLevel level, int x, int y, int z, BoundingBox bb) {
            BlockPos pos = new BlockPos(x, y, z);
            if (bb.isInside(pos)) {
                level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
                if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
                    container.setLootTable(GrandterrainLootTables.CASTLE_ARMORY, pos.asLong());
                }
            }
        }

        private void placeSpawner(WorldGenLevel level, RandomSource random,
                                   int x, int y, int z, BoundingBox bb) {
            BlockPos pos = new BlockPos(x, y, z);
            if (bb.isInside(pos)) {
                level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), 2);
                if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity sbe) {
                    int hash = ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 0x7FFFFFFF;
                    sbe.setEntityId(EntityType.SKELETON, RandomSource.create(hash));
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
