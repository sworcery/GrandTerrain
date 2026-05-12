package com.grandterrain.worldgen.structure;

import com.grandterrain.Grandterrain;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
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
}
