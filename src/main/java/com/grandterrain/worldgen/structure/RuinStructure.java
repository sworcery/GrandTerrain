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
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/**
 * Scattered stone ruins - remnants of ancient structures.
 */
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
        return Optional.of(new GenerationStub(pos, builder -> {
            builder.addPiece(new RuinPiece(pos, context.random()));
        }));
    }

    public static class RuinPiece extends StructurePiece {

        private final int baseY;
        private final int variant;

        public RuinPiece(BlockPos pos, RandomSource random) {
            super(GrandterrainStructurePieces.RUIN_PIECE, 0,
                    new BoundingBox(pos.getX() - 6, pos.getY() - 1, pos.getZ() - 6,
                            pos.getX() + 6, pos.getY() + 8, pos.getZ() + 6));
            this.baseY = pos.getY();
            this.variant = random.nextInt(3);
        }

        public RuinPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(GrandterrainStructurePieces.RUIN_PIECE, tag);
            this.baseY = tag.getIntOr("baseY", 64);
            this.variant = tag.getIntOr("variant", 0);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
            tag.putInt("baseY", baseY);
            tag.putInt("variant", variant);
        }

        @Override
        public void postProcess(WorldGenLevel level, StructureManager structureManager,
                                ChunkGenerator generator, RandomSource random,
                                BoundingBox chunkBB, ChunkPos chunkPos, BlockPos pivot) {
            int cx = (boundingBox.minX() + boundingBox.maxX()) / 2;
            int cz = (boundingBox.minZ() + boundingBox.maxZ()) / 2;
            int y = baseY;

            switch (variant) {
                case 0 -> generateBrokenWalls(level, random, cx, y, cz, chunkBB);
                case 1 -> generateRuinedTower(level, random, cx, y, cz, chunkBB);
                case 2 -> generateFoundation(level, random, cx, y, cz, chunkBB);
            }
        }

        private void generateBrokenWalls(WorldGenLevel level, RandomSource random,
                                          int cx, int y, int cz, BoundingBox bb) {
            BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
            BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();

            for (int i = -5; i <= 5; i++) {
                int maxH = 1 + random.nextInt(4);
                for (int h = 0; h <= maxH; h++) {
                    if (random.nextInt(3) == 0) continue; // Gaps
                    BlockState block = random.nextInt(3) == 0 ? mossy : stone;
                    setBlock(level, block, cx + i, y + h, cz - 5, bb);
                }
                maxH = 1 + random.nextInt(3);
                for (int h = 0; h <= maxH; h++) {
                    if (random.nextInt(3) == 0) continue;
                    BlockState block = random.nextInt(3) == 0 ? mossy : stone;
                    setBlock(level, block, cx - 5, y + h, cz + i, bb);
                }
            }
        }

        private void generateRuinedTower(WorldGenLevel level, RandomSource random,
                                          int cx, int y, int cz, BoundingBox bb) {
            BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
            BlockState cracked = Blocks.CRACKED_STONE_BRICKS.defaultBlockState();

            int maxHeight = 4 + random.nextInt(5);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean isWall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (!isWall) continue;

                    int colHeight = maxHeight - random.nextInt(3);
                    for (int h = 0; h <= colHeight; h++) {
                        if (h > 2 && random.nextInt(4) == 0) continue;
                        BlockState block = random.nextInt(3) == 0 ? cracked : stone;
                        setBlock(level, block, cx + dx, y + h, cz + dz, bb);
                    }
                }
            }
        }

        private void generateFoundation(WorldGenLevel level, RandomSource random,
                                         int cx, int y, int cz, BoundingBox bb) {
            BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
            BlockState mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();

            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (random.nextInt(4) == 0) continue;
                    BlockState block = random.nextInt(2) == 0 ? mossy : stone;
                    setBlock(level, block, cx + dx, y - 1, cz + dz, bb);
                    if (random.nextInt(3) == 0) {
                        setBlock(level, block, cx + dx, y, cz + dz, bb);
                    }
                }
            }

            // Chest in center sometimes
            if (random.nextInt(3) == 0) {
                setBlock(level, Blocks.CHEST.defaultBlockState(), cx, y, cz, bb);
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
