package com.grandterrain.worldgen.structure;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
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

public class RuinStructure extends Structure {

    public static final MapCodec<RuinStructure> CODEC = simpleCodec(RuinStructure::new);

    public RuinStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return GrandterrainStructures.RUIN;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (context.chunkGenerator() instanceof com.grandterrain.worldgen.GrandterrainChunkGenerator gen
                && !gen.getConfigSnapshot().enableRuins()) {
            return Optional.empty();
        }

        ChunkPos chunkPos = context.chunkPos();
        int x = chunkPos.getMiddleBlockX();
        int z = chunkPos.getMiddleBlockZ();
        int y = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());

        if (y < 135 || y > 500) return Optional.empty();

        BlockPos pos = new BlockPos(x, y, z);
        int variant = context.random().nextInt(4);
        int ruinHeight = switch (variant) {
            case 0 -> 20 + context.random().nextInt(21);
            case 3 -> 12 + context.random().nextInt(9);
            default -> 0;
        };

        return Optional.of(new GenerationStub(pos, builder -> {
            builder.addPiece(new RuinPiece(pos, variant, ruinHeight));
        }));
    }

    public static class RuinPiece extends StructurePiece {

        private final int baseY;
        private final int variant;
        private final int ruinHeight;

        public RuinPiece(BlockPos pos, int variant, int ruinHeight) {
            super(GrandterrainStructurePieces.RUIN_PIECE, 0,
                    new BoundingBox(pos.getX() - 12, pos.getY() - 4, pos.getZ() - 12,
                            pos.getX() + 12, pos.getY() + 45, pos.getZ() + 12));
            this.baseY = pos.getY();
            this.variant = variant;
            this.ruinHeight = ruinHeight;
        }

        public RuinPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(GrandterrainStructurePieces.RUIN_PIECE, tag);
            this.baseY = tag.getIntOr("baseY", 64);
            this.variant = tag.getIntOr("variant", 0);
            this.ruinHeight = tag.getIntOr("ruinHeight", 25);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("baseY", baseY);
            tag.putInt("variant", variant);
            tag.putInt("ruinHeight", ruinHeight);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager,
                                ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBB, ChunkPos chunkPos, BlockPos pivot) {
            int cx = (boundingBox.minX() + boundingBox.maxX()) / 2;
            int cz = (boundingBox.minZ() + boundingBox.maxZ()) / 2;
            int y = baseY;

            switch (variant) {
                case 0 -> generateCollapsedTower(level, random, cx, y, cz, ruinHeight, chunkBB);
                case 1 -> generateRuinedWalls(level, random, cx, y, cz, chunkBB);
                case 2 -> generateSunkenFoundation(level, random, cx, y, cz, chunkBB);
                case 3 -> generateRuinedKeep(level, random, cx, y, cz, ruinHeight, chunkBB);
            }
        }

        private void generateCollapsedTower(WorldGenLevel level, RandomSource random,
                                             int cx, int y, int cz, int height,
                                             BoundingBox bb) {
            int halfW = 4;

            for (int dx = -(halfW + 2); dx <= halfW + 2; dx++) {
                for (int dz = -(halfW + 2); dz <= halfW + 2; dz++) {
                    if (Math.abs(dx) <= halfW && Math.abs(dz) <= halfW) continue;
                    int rubbleH = posHash(cx + dx, y, cz + dz) % 3;
                    if (posHash(cx + dx, y + 1, cz + dz) % 3 == 0) continue;
                    for (int h = 0; h <= rubbleH; h++) {
                        BlockState rubble = (posHash(cx + dx, y + h, cz + dz) & 3) == 0
                                ? Blocks.GRAVEL.defaultBlockState()
                                : Blocks.COBBLESTONE.defaultBlockState();
                        setBlock(level, rubble, cx + dx, y + h, cz + dz, bb);
                    }
                }
            }

            for (int h = 1; h <= height; h++) {
                double decay = (double) h / height;
                for (int dx = -halfW; dx <= halfW; dx++) {
                    for (int dz = -halfW; dz <= halfW; dz++) {
                        boolean isEdge = Math.abs(dx) == halfW || Math.abs(dz) == halfW;
                        if (!isEdge) {
                            setBlock(level, Blocks.AIR.defaultBlockState(),
                                     cx + dx, y + h, cz + dz, bb);
                            continue;
                        }

                        if (h > height / 4 && shouldDecay(cx + dx, y + h, cz + dz, decay * decay * 0.7)) {
                            continue;
                        }

                        boolean isWindow = (h % 8 == 5)
                                && h < height * 0.7
                                && ((dx == 0 && Math.abs(dz) == halfW) ||
                                    (dz == 0 && Math.abs(dx) == halfW));
                        if (isWindow) {
                            setBlock(level, Blocks.IRON_BARS.defaultBlockState(),
                                     cx + dx, y + h, cz + dz, bb);
                        } else {
                            setBlock(level, decayedStone(cx + dx, y + h, cz + dz, decay),
                                     cx + dx, y + h, cz + dz, bb);
                        }
                    }
                }
            }

            int floorY = y + height / 3;
            for (int dx = -(halfW - 1); dx <= halfW - 1; dx++) {
                for (int dz = -(halfW - 1); dz <= halfW - 1; dz++) {
                    if (!shouldDecay(cx + dx, floorY, cz + dz, 0.25)) {
                        setBlock(level, Blocks.OAK_PLANKS.defaultBlockState(),
                                 cx + dx, floorY, cz + dz, bb);
                    }
                }
            }

            if (posHash(cx, floorY + 1, cz) % 2 == 0) {
                placeChest(level, cx, floorY + 1, cz, bb);
            }
            if (posHash(cx, y + 1, cz) % 3 == 0) {
                placeSpawner(level, random, cx, y + 1, cz, bb);
            }
        }

        private void generateRuinedWalls(WorldGenLevel level, RandomSource random,
                                          int cx, int y, int cz, BoundingBox bb) {
            int length1 = 10 + (posHash(cx, y, cz) % 6);
            int length2 = 6 + (posHash(cx + 1, y, cz) % 6);

            for (int i = 0; i < length1; i++) {
                int wx = cx - length1 / 2 + i;
                int maxH = 3 + (posHash(wx, y + 10, cz) % 5);
                for (int h = 0; h <= maxH; h++) {
                    if (h > 2 && shouldDecay(wx, y + h, cz, 0.3)) continue;
                    BlockState block = (posHash(wx, y + h, cz) & 3) == 0
                            ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                            : Blocks.COBBLESTONE.defaultBlockState();
                    setBlock(level, block, wx, y + h, cz, bb);
                    if (h <= 2) {
                        setBlock(level, block, wx, y + h, cz + 1, bb);
                    }
                }
            }

            int cornerX = cx + length1 / 2 - 1;
            for (int i = 1; i <= length2; i++) {
                int wz = cz - i;
                int maxH = 2 + (posHash(cornerX, y + 10, wz) % 4);
                for (int h = 0; h <= maxH; h++) {
                    if (h > 1 && shouldDecay(cornerX, y + h, wz, 0.35)) continue;
                    BlockState block = (posHash(cornerX, y + h, wz) & 3) == 0
                            ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                            : Blocks.COBBLESTONE.defaultBlockState();
                    setBlock(level, block, cornerX, y + h, wz, bb);
                    if (h <= 1) {
                        setBlock(level, block, cornerX - 1, y + h, wz, bb);
                    }
                }
            }

            for (int dx = -2; dx <= 1; dx++) {
                for (int dz = -2; dz <= 0; dz++) {
                    if (!shouldDecay(cornerX + dx, y, cz + dz, 0.5)) {
                        setBlock(level, Blocks.COBBLESTONE.defaultBlockState(),
                                 cornerX + dx, y, cz + dz, bb);
                    }
                }
            }

            if (posHash(cornerX - 1, y + 1, cz - 1) % 2 == 0) {
                placeChest(level, cornerX - 1, y + 1, cz - 1, bb);
            }
        }

        private void generateSunkenFoundation(WorldGenLevel level, RandomSource random,
                                               int cx, int y, int cz, BoundingBox bb) {
            int halfW = 6;

            for (int dx = -halfW; dx <= halfW; dx++) {
                for (int dz = -halfW; dz <= halfW; dz++) {
                    boolean isEdge = Math.abs(dx) == halfW || Math.abs(dz) == halfW;
                    if (!isEdge) continue;
                    for (int h = -2; h <= 1; h++) {
                        if (h > 0 && shouldDecay(cx + dx, y + h, cz + dz, 0.3)) continue;
                        setBlock(level, decayedStone(cx + dx, y + h, cz + dz, 0.5),
                                 cx + dx, y + h, cz + dz, bb);
                    }
                }
            }

            int chamberHW = 3;
            int chamberFloor = y - 3;
            for (int dx = -chamberHW; dx <= chamberHW; dx++) {
                for (int dz = -chamberHW; dz <= chamberHW; dz++) {
                    setBlock(level, Blocks.COBBLESTONE.defaultBlockState(),
                             cx + dx, chamberFloor, cz + dz, bb);
                    for (int h = 1; h <= 3; h++) {
                        setBlock(level, Blocks.AIR.defaultBlockState(),
                                 cx + dx, chamberFloor + h, cz + dz, bb);
                    }
                    setBlock(level, Blocks.STONE_BRICKS.defaultBlockState(),
                             cx + dx, chamberFloor + 4, cz + dz, bb);
                }
            }

            for (int step = 0; step < 3; step++) {
                for (int dx = -1; dx <= 1; dx++) {
                    setBlock(level, Blocks.COBBLESTONE.defaultBlockState(),
                             cx + dx, y - step - 1, cz + halfW - step, bb);
                    setBlock(level, Blocks.AIR.defaultBlockState(),
                             cx + dx, y - step, cz + halfW - step, bb);
                    setBlock(level, Blocks.AIR.defaultBlockState(),
                             cx + dx, y - step + 1, cz + halfW - step, bb);
                }
            }

            placeChest(level, cx + 1, chamberFloor + 1, cz, bb);
            if (posHash(cx - 1, chamberFloor + 1, cz) % 2 == 0) {
                placeSpawner(level, random, cx - 1, chamberFloor + 1, cz, bb);
            }
            setBlock(level, Blocks.TORCH.defaultBlockState(),
                     cx, chamberFloor + 1, cz - 2, bb);
        }

        private void generateRuinedKeep(WorldGenLevel level, RandomSource random,
                                          int cx, int y, int cz, int height,
                                          BoundingBox bb) {
            int halfW = 6;

            for (int h = 0; h <= height; h++) {
                double decay = (double) h / height;
                for (int dx = -halfW; dx <= halfW; dx++) {
                    for (int dz = -halfW; dz <= halfW; dz++) {
                        boolean isEdge = Math.abs(dx) == halfW || Math.abs(dz) == halfW;
                        if (isEdge) {
                            if (h > height / 3 && shouldDecay(cx + dx, y + h, cz + dz, decay * 0.6)) {
                                continue;
                            }
                            setBlock(level, decayedStone(cx + dx, y + h, cz + dz, decay),
                                     cx + dx, y + h, cz + dz, bb);
                        } else if (h >= 1) {
                            setBlock(level, Blocks.AIR.defaultBlockState(),
                                     cx + dx, y + h, cz + dz, bb);
                        }
                    }
                }
            }

            int partitionH = height / 2 + (posHash(cx, y, cz + 1) % 3);
            for (int dx = -(halfW - 1); dx <= halfW - 1; dx++) {
                for (int h = 0; h <= partitionH; h++) {
                    if (h > partitionH / 2 && shouldDecay(cx + dx, y + h, cz, 0.3)) continue;
                    setBlock(level, decayedStone(cx + dx, y + h, cz, 0.4),
                             cx + dx, y + h, cz, bb);
                }
            }
            for (int h = 1; h <= 3; h++) {
                setBlock(level, Blocks.AIR.defaultBlockState(), cx, y + h, cz, bb);
            }

            int floorY = y + height / 2;
            for (int dx = -(halfW - 1); dx <= halfW - 1; dx++) {
                for (int dz = -(halfW - 1); dz <= halfW - 1; dz++) {
                    if (dz == 0 && Math.abs(dx) <= 1) continue;
                    if (!shouldDecay(cx + dx, floorY, cz + dz, 0.6)) {
                        setBlock(level, Blocks.OAK_PLANKS.defaultBlockState(),
                                 cx + dx, floorY, cz + dz, bb);
                    }
                }
            }

            for (int h = 1; h <= 3; h++) {
                setBlock(level, Blocks.AIR.defaultBlockState(),
                         cx, y + h, cz + halfW, bb);
            }

            placeChest(level, cx + halfW - 2, y + 1, cz - halfW + 2, bb);
            if (posHash(cx - halfW + 2, floorY + 1, cz + 2) % 2 == 0) {
                placeChest(level, cx - halfW + 2, floorY + 1, cz + 2, bb);
            }
            if (posHash(cx + halfW - 2, y + 1, cz + halfW - 2) % 2 == 0) {
                placeSpawner(level, random, cx + halfW - 2, y + 1, cz + halfW - 2, bb);
            }
        }

        private boolean shouldDecay(int x, int y, int z, double chance) {
            int hash = ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 0xFF;
            return hash < (int) (chance * 256);
        }

        private int posHash(int x, int y, int z) {
            return ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 0x7FFFFFFF;
        }

        private BlockState decayedStone(int x, int y, int z, double decay) {
            int hash = posHash(x, y, z);
            if (decay > 0.6) {
                return switch (hash % 4) {
                    case 0 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                    case 1 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                    case 2 -> Blocks.COBBLESTONE.defaultBlockState();
                    default -> Blocks.STONE_BRICKS.defaultBlockState();
                };
            }
            return switch (hash % 6) {
                case 0 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                case 1 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                default -> Blocks.STONE_BRICKS.defaultBlockState();
            };
        }

        private void placeChest(WorldGenLevel level, int x, int y, int z, BoundingBox bb) {
            BlockPos pos = new BlockPos(x, y, z);
            if (bb.isInside(pos)) {
                level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
                if (level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container) {
                    container.setLootTable(GrandterrainLootTables.RUIN_TREASURE, pos.asLong());
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
                    sbe.setEntityId(EntityType.ZOMBIE, RandomSource.create(hash));
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
