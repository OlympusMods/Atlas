package com.github.olympus.atlas.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Constructor;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeyFactory {
    private static final int UNBOUND = InputConstants.UNKNOWN.getValue();
    private static final boolean SDL = isSdl();

    private KeyFactory() {
    }

    public static KeyMapping.Category registerCategory() {
        return KeyMapping.Category.register(AtlasClient.id("main"));
    }

    public static KeyBindings create(KeyMapping.Category category) {
        return new KeyBindings(key("map", GLFW.GLFW_KEY_M, 16, category),
                key("waypoint", GLFW.GLFW_KEY_B, 5, category),
                key("temporary", UNBOUND, UNBOUND, category),
                key("minimap", GLFW.GLFW_KEY_H, 11, category),
                key("zoomIn", GLFW.GLFW_KEY_EQUAL, 46, category),
                key("zoomOut", GLFW.GLFW_KEY_MINUS, 45, category),
                key("view", GLFW.GLFW_KEY_V, 25, category),
                key("cave", UNBOUND, UNBOUND, category),
                key("waypoints", UNBOUND, UNBOUND, category),
                key("menu", UNBOUND, UNBOUND, category));
    }

    private static boolean isSdl() {
        for (Object constant : InputConstants.Type.class.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals("KEYBOARD")) {
                return true;
            }
        }
        return false;
    }

    private static KeyMapping key(String name, int glfw, int sdl, KeyMapping.Category category) {
        String translationKey = "key.atlas." + name;
        int code = SDL ? sdl : glfw;
        try {
            Constructor<KeyMapping> withoutType =
                    findConstructor(String.class, int.class, KeyMapping.Category.class);
            if (withoutType != null) {
                return withoutType.newInstance(translationKey, code, category);
            }
            Constructor<KeyMapping> withType = findConstructor(
                    String.class, InputConstants.Type.class, int.class, KeyMapping.Category.class);
            if (withType != null) {
                return withType.newInstance(translationKey, keyboardType(), code, category);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create the key mapping " + translationKey, exception);
        }
        throw new IllegalStateException("This Minecraft version has no supported KeyMapping constructor");
    }

    private static Constructor<KeyMapping> findConstructor(Class<?>... parameters) {
        try {
            return KeyMapping.class.getConstructor(parameters);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static InputConstants.Type keyboardType() {
        String name = SDL ? "KEYBOARD" : "KEYSYM";
        for (InputConstants.Type type : InputConstants.Type.class.getEnumConstants()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        throw new IllegalStateException("InputConstants.Type has no " + name);
    }
}
