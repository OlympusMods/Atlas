package com.github.olympus.atlas.client;

import com.github.olympus.atlas.mixin.BobMixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

public final class Cam {
    private Cam() {
    }

    private static Camera camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    public static void prepare() {
        Minecraft minecraft = Minecraft.getInstance();
        GameRenderer renderer = minecraft.gameRenderer;
        GameRenderState state = renderer.getGameRenderState();
        CameraRenderState view = state.levelRenderState.cameraRenderState;
        PoseStack pose = new PoseStack();
        BobMixin bob = (BobMixin) renderer;
        bob.atlas$bobHurt(view, pose);
        if (state.optionsRenderState.bobView) {
            bob.atlas$bobView(view, pose);
        }
        Projection.sway(pose.last().pose(), 1.0);
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
