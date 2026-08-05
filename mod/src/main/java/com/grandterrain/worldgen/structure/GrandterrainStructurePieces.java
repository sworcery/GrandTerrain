package com.grandterrain.worldgen.structure;

import com.grandterrain.Grandterrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public class GrandterrainStructurePieces {

    public static final StructurePieceType CASTLE_PIECE =
            register("castle_piece", CastleStructure.CastlePiece::new);

    public static final StructurePieceType RUIN_PIECE =
            register("ruin_piece", RuinStructure.RuinPiece::new);

    public static final StructurePieceType DUNGEON_PIECE =
            register("dungeon_piece", DungeonStructure.DungeonPiece::new);

    public static final StructurePieceType WATCHTOWER_PIECE =
            register("watchtower_piece", WatchtowerStructure.WatchtowerPiece::new);

    private static StructurePieceType register(String name, StructurePieceType type) {
        return Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
                Grandterrain.id(name), type);
    }

    public static void register() {
        // Force class loading
    }

    /**
     * Fill a foundation column from topY downward (absolute world coords)
     * until it meets solid, non-fluid ground. Substitute for vanilla's
     * Beardifier terrain adaptation, which only runs inside
     * NoiseBasedChunkGenerator and is therefore inert for this mod's custom
     * generator — without this, fixed-depth foundations float on slopes.
     * Bounded to 48 blocks as a safety stop on pathological terrain.
     */
    public static void anchorColumn(WorldGenLevel level, BlockState state,
                                    int x, int topY, int z, BoundingBox bb) {
        // Column-level early-out: chunkBB is full-height, so x/z alone decide
        // whether any write can land. Without this, every chunk pass reads
        // 48 blocks per out-of-chunk column — including neighbor chunks that
        // are still at STRUCTURE_STARTS status and read as all-air.
        if (x < bb.minX() || x > bb.maxX() || z < bb.minZ() || z > bb.maxZ()) return;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, topY, z);
        int stop = Math.max(level.getMinY() + 8, topY - 48);
        while (p.getY() >= stop) {
            BlockState cur = level.getBlockState(p);
            // Continue through air, fluids, and replaceable deco (vanilla
            // isReplaceableByStructures parity: glow lichen, grass, etc.).
            if (!cur.isAir() && cur.getFluidState().isEmpty() && !cur.canBeReplaced()) break;
            if (bb.isInside(p)) {
                level.setBlock(p, state, 2);
            }
            p.move(0, -1, 0);
        }
    }
}
