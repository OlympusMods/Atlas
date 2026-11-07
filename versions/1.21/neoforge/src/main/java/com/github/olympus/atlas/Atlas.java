package com.github.olympus.atlas;

import com.github.olympus.atlas.client.AtlasMenu;
import com.github.olympus.atlas.client.AtlasClient;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@Mod(value = Atlas.MOD_ID, dist = Dist.CLIENT)
public class Atlas {
    public static final String MOD_ID = "atlas";

    public Atlas(IEventBus modBus) {
        modBus.addListener(Atlas::onRegisterKeys);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        AtlasClient.configure(FMLPaths.CONFIGDIR.get());
        AtlasMenu.register();
        for (KeyMapping key : AtlasClient.keys().all()) {
            event.register(key);
        }
    }
}
