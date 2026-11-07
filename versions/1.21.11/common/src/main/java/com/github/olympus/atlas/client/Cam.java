package com.github.olympus.atlas.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

public final class Cam {
    private Cam() {
    }

    private static Camera camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    public static boolean ready() {
        return camera().isInitialized();
    }

    public static double x() {
        return camera().position().x;
    }

    public static double y() {
        return camera().position().y;
    }

    public static double z() {
        return camera().position().z;
    }

    public static float yaw() {
        return camera().yRot();
    }

    public static float pitch() {
        return camera().xRot();
    }
}
