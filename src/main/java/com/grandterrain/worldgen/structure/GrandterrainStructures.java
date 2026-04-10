package com.grandterrain.worldgen.structure;

import com.grandterrain.Grandterrain;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;

public class GrandterrainStructures {

    public static final StructureType<CastleStructure> CASTLE = () -> CastleStructure.CODEC;
    public static final StructureType<RuinStructure> RUIN = () -> RuinStructure.CODEC;
    public static final StructureType<DungeonStructure> DUNGEON = () -> DungeonStructure.CODEC;

    public static void register() {
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Grandterrain.id("castle"), CASTLE);
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Grandterrain.id("ruin"), RUIN);
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
                Grandterrain.id("dungeon"), DUNGEON);
    }
}
