package com.github.olympus.atlas;

import com.github.olympus.atlas.client.AtlasMenu;
import com.github.olympus.atlas.client.AtlasClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

public final class AtlasFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AtlasClient.configure(FabricLoader.getInstance().getConfigDir());
        AtlasMenu.register();
        for (KeyMapping key : AtlasClient.keys().all()) {
            KeyBindingHelper.registerKeyBinding(key);
        }
    }
}
