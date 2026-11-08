package com.github.olympus.atlas.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;

public final class Cam {
    private Cam() {
    }

    private static Camera camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    public static void prepare() {
    }

    public static boolean ready() {
        return camera().isInitialized();
    }

    public static double x() {
        return camera().getPosition().x;
    }

    public static double y() {
        return camera().getPosition().y;
    }

    public static double z() {
        return camera().getPosition().z;
    }

    public static float yaw() {
        return camera().getYRot();
    }

    public static float pitch() {
        return camera().getXRot();
    }
}
