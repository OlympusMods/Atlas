package com.github.olympus.atlas.client;

import com.mojang.blaze3d.systems.RenderSystem;

public final class Blend {
    private Blend() {
    }

    public static void on() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
    }
}
