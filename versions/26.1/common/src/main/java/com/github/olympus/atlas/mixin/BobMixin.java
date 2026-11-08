package com.github.olympus.atlas.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface BobMixin {
    @Invoker("bobHurt")
    void atlas$bobHurt(CameraRenderState camera, PoseStack pose);

    @Invoker("bobView")
    void atlas$bobView(CameraRenderState camera, PoseStack pose);
}
