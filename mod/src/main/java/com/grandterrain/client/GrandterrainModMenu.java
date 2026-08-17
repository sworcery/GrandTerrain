package com.grandterrain.client;

import com.grandterrain.config.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.loader.api.FabricLoader;

public class GrandterrainModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // Cloth Config is optional. Without this guard, clicking "Config" with
        // ModMenu installed but Cloth absent surfaces a raw NoClassDefFoundError
        // tooltip. Returning null makes ModMenu hide the button cleanly.
        return FabricLoader.getInstance().isModLoaded("cloth-config")
                ? ConfigScreen::create
                : null;
    }
}
