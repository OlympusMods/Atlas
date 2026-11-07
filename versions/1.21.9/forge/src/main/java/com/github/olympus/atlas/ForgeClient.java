package com.github.olympus.atlas;

import com.github.olympus.atlas.client.AtlasMenu;
import com.github.olympus.atlas.client.AtlasClient;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.fml.loading.FMLPaths;

public final class ForgeClient {
    private ForgeClient() {
    }

    static void register() {
        RegisterKeyMappingsEvent.BUS.addListener(ForgeClient::onRegisterKeys);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        AtlasClient.configure(FMLPaths.CONFIGDIR.get());
        AtlasMenu.register();
        for (KeyMapping key : AtlasClient.keys().all()) {
            event.register(key);
        }
    }
}
