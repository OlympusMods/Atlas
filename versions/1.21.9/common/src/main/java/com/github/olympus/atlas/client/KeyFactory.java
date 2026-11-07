package com.github.olympus.atlas.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeyFactory {
    private static final int UNBOUND = InputConstants.UNKNOWN.getValue();
    private KeyFactory() {
    }

    public static KeyMapping.Category registerCategory() {
        return KeyMapping.Category.register(AtlasClient.id("main"));
    }

    public static KeyBindings create(KeyMapping.Category category) {
        return new KeyBindings(key("map", GLFW.GLFW_KEY_M, category),
                key("waypoint", GLFW.GLFW_KEY_B, category),
                key("temporary", UNBOUND, category),
                key("minimap", GLFW.GLFW_KEY_H, category),
                key("zoomIn", GLFW.GLFW_KEY_EQUAL, category),
                key("zoomOut", GLFW.GLFW_KEY_MINUS, category),
                key("view", GLFW.GLFW_KEY_V, category),
                key("cave", UNBOUND, category),
                key("waypoints", UNBOUND, category),
                key("menu", UNBOUND, category));
    }

    private static KeyMapping key(String name, int code, KeyMapping.Category category) {
        return new KeyMapping("key.atlas." + name, InputConstants.Type.KEYSYM, code, category);
    }
}
