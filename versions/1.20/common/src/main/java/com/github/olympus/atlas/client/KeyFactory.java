package com.github.olympus.atlas.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeyFactory {
    private static final int UNBOUND = InputConstants.UNKNOWN.getValue();
    private static final String CATEGORY = "key.categories.atlas";

    private KeyFactory() {
    }

    public static KeyBindings create() {
        return new KeyBindings(key("map", GLFW.GLFW_KEY_M),
                key("waypoint", GLFW.GLFW_KEY_B),
                key("temporary", UNBOUND),
                key("minimap", GLFW.GLFW_KEY_H),
                key("zoomIn", GLFW.GLFW_KEY_EQUAL),
                key("zoomOut", GLFW.GLFW_KEY_MINUS),
                key("view", GLFW.GLFW_KEY_V),
                key("cave", UNBOUND),
                key("waypoints", UNBOUND),
                key("menu", UNBOUND));
    }

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.atlas." + name, InputConstants.Type.KEYSYM, code, CATEGORY);
    }
}
