package com.grandterrain.worldgen.structure;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

public class GrandterrainLootTables {

    public static final ResourceKey<LootTable> DUNGEON_COMMON =
            register("chests/dungeon_common");
    public static final ResourceKey<LootTable> DUNGEON_BOSS =
            register("chests/dungeon_boss");
    public static final ResourceKey<LootTable> RUIN_TREASURE =
            register("chests/ruin_treasure");
    public static final ResourceKey<LootTable> CASTLE_ARMORY =
            register("chests/castle_armory");
    public static final ResourceKey<LootTable> WATCHTOWER_SUPPLY =
            register("chests/watchtower_supply");

    private static ResourceKey<LootTable> register(String path) {
        return ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath("grandterrain", path));
    }
}
