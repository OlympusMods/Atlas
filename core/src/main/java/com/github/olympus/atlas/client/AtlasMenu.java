package com.github.olympus.atlas.client;

import com.github.olympus.atlas.client.ui.AtlasUi;
import com.github.olympus.atlas.client.ui.HudOverlay;
import com.github.olympus.proteus.client.ProteusScreen;
import com.github.olympus.proteus.client.ui.Hub;
import com.github.olympus.proteus.client.ui.Overlays;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class AtlasMenu {
    private static boolean registered;

    private AtlasMenu() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        Hub.register(AtlasClient.MOD_ID, () -> Component.translatable("atlas.screen.title").getString(), "filled_map",
                AtlasUi::new);
        Overlays.add(new HudOverlay());
    }

    public static Screen create(Screen parent) {
        return ProteusScreen.open(parent, AtlasClient.MOD_ID);
    }
}
