package com.grandterrain.mixin;

import com.grandterrain.Grandterrain;
import com.grandterrain.worldgen.GrandterrainChunkGenerator;
import com.grandterrain.worldgen.biome.GrandterrainBiomeSource;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.levelgen.presets.WorldPresets$Bootstrap")
public abstract class WorldPresetsRegistrarMixin {

    @Shadow
    private HolderGetter<Biome> biomes;

    @Shadow
    private Holder<DimensionType> overworldDimensionType;

    @Shadow
    abstract LevelStem makeOverworld(net.minecraft.world.level.chunk.ChunkGenerator generator);

    @Shadow
    abstract void registerCustomOverworldPreset(ResourceKey<WorldPreset> key, LevelStem overworldStem);

    @Inject(method = "bootstrap", at = @At("TAIL"))
    private void grandterrain$registerPreset(CallbackInfo ci) {
        ResourceKey<WorldPreset> key = ResourceKey.create(
                net.minecraft.core.registries.Registries.WORLD_PRESET,
                Grandterrain.id("grandterrain")
        );

        GrandterrainBiomeSource biomeSource = new GrandterrainBiomeSource(biomes);
        GrandterrainChunkGenerator generator = new GrandterrainChunkGenerator(biomeSource);
        LevelStem overworldStem = makeOverworld(generator);

        registerCustomOverworldPreset(key, overworldStem);
    }
}
