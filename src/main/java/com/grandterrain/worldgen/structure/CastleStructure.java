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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/**
 * Procedurally generated castle structure with walls, towers, and a keep.
 */
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
        // Respect config toggle
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

        if (y < 140 || y > 350) return Optional.empty();

        // Flatness check: reject if the 12-block footprint has >8 blocks of relief
        int yN = context.chunkGenerator().getBaseHeight(x, z - 12, hm,
                context.heightAccessor(), context.randomState());
        int yS = context.chunkGenerator().getBaseHeight(x, z + 12, hm,
                context.heightAccessor(), context.randomState());
        int yE = context.chunkGenerator().getBaseHeight(x + 12, z, hm,
                context.heightAccessor(), context.randomState());
        int yW = context.chunkGenerator().getBaseHeight(x - 12, z, hm,
                context.heightAccessor(), context.randomState());
        int maxDelta = Math.max(Math.max(Math.abs(y - yN), Math.abs(y - yS)),
                Math.max(Math.abs(y - yE), Math.abs(y - yW)));
        if (maxDelta > 8) return Optional.empty();

        BlockPos pos = new BlockPos(x, y, z);

        return Optional.of(new GenerationStub(pos, builder -> {
            builder.addPiece(new CastlePiece(pos, context.random()));
        }));
    }

    public static class CastlePiece extends StructurePiece {

        private final int baseY;

        public CastlePiece(BlockPos pos, RandomSource random) {
            super(GrandterrainStructurePieces.CASTLE_PIECE, 0,
                    new BoundingBox(pos.getX() - 15, pos.getY() - 2, pos.getZ() - 15,
                            pos.getX() + 15, pos.getY() + 25, pos.getZ() + 15));
            this.baseY = pos.getY();
        }

        public CastlePiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
            super(GrandterrainStructurePieces.CASTLE_PIECE, tag);
            this.baseY = tag.getIntOr("baseY", 64);
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

            BlockState stone = Blocks.STONE_BRICKS.defaultBlockState();
            BlockState mossy = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();

            // Foundation platform
            for (int dx = -12; dx <= 12; dx++) {
                for (int dz = -12; dz <= 12; dz++) {
                    for (int dy = -2; dy <= 0; dy++) {
                        setBlock(level, stone, cx + dx, y + dy, cz + dz, chunkBB);
                    }
                }
            }

            // Outer walls (square, 24x24)
            for (int i = -12; i <= 12; i++) {
                for (int h = 1; h <= 8; h++) {
                    BlockState wall = (random.nextInt(5) == 0) ? mossy : stone;
                    setBlock(level, wall, cx - 12, y + h, cz + i, chunkBB);
                    setBlock(level, wall, cx + 12, y + h, cz + i, chunkBB);
                    setBlock(level, wall, cx + i, y + h, cz - 12, chunkBB);
                    setBlock(level, wall, cx + i, y + h, cz + 12, chunkBB);
                }
            }

            // Battlements on walls
            for (int i = -12; i <= 12; i += 2) {
                setBlock(level, stone, cx - 12, y + 9, cz + i, chunkBB);
                setBlock(level, stone, cx + 12, y + 9, cz + i, chunkBB);
                setBlock(level, stone, cx + i, y + 9, cz - 12, chunkBB);
                setBlock(level, stone, cx + i, y + 9, cz + 12, chunkBB);
            }

            // Four corner towers (3x3, height 14)
            int[][] towers = {{-12, -12}, {-12, 12}, {12, -12}, {12, 12}};
            for (int[] tower : towers) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int h = 1; h <= 14; h++) {
                            BlockState tw = (dx == 0 && dz == 0) ? Blocks.AIR.defaultBlockState() :
                                    (random.nextInt(4) == 0 ? mossy : stone);
                            setBlock(level, tw, cx + tower[0] + dx, y + h, cz + tower[1] + dz, chunkBB);
                        }
                        // Tower cap
                        setBlock(level, cobble, cx + tower[0] + dx, y + 15, cz + tower[1] + dz, chunkBB);
                    }
                }
            }

            // Central keep (6x6, height 12)
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    boolean isWall = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    for (int h = 1; h <= 12; h++) {
                        if (isWall) {
                            // Door opening on south side
                            if (dz == 3 && Math.abs(dx) <= 1 && h <= 3) {
                                continue;
                            }
                            // Windows
                            if (h == 6 && (dx == 0 || dz == 0) && (Math.abs(dx) == 3 || Math.abs(dz) == 3)) {
                                if (random.nextInt(2) == 0) continue;
                            }
                            setBlock(level, stone, cx + dx, y + h, cz + dz, chunkBB);
                        }
                    }
                    // Keep floor
                    setBlock(level, Blocks.OAK_PLANKS.defaultBlockState(), cx + dx, y + 1, cz + dz, chunkBB);
                    // Keep roof
                    setBlock(level, cobble, cx + dx, y + 13, cz + dz, chunkBB);
                }
            }

            // Courtyard floor (between walls and keep)
            for (int dx = -11; dx <= 11; dx++) {
                for (int dz = -11; dz <= 11; dz++) {
                    if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) continue;
                    BlockState floor = random.nextInt(3) == 0 ?
                            Blocks.COBBLESTONE.defaultBlockState() :
                            Blocks.STONE_BRICKS.defaultBlockState();
                    setBlock(level, floor, cx + dx, y, cz + dz, chunkBB);
                }
            }

            // Gate opening in south wall
            for (int dx = -1; dx <= 1; dx++) {
                for (int h = 1; h <= 4; h++) {
                    setBlock(level, Blocks.AIR.defaultBlockState(), cx + dx, y + h, cz + 12, chunkBB);
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
