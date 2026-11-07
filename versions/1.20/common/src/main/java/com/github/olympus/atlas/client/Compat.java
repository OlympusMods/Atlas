package com.github.olympus.atlas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class Compat {
    private Compat() {
    }

    public static Screen screen(Minecraft minecraft) {
        return minecraft.screen;
    }

    public static void show(Minecraft minecraft, Screen screen) {
        minecraft.setScreen(screen);
    }

    public static void echo(Minecraft minecraft, String text) {
        minecraft.player.displayClientMessage(Component.literal(text), false);
    }

    public static boolean permitted(LocalPlayer player) {
        return player.hasPermissions(2);
    }
}
