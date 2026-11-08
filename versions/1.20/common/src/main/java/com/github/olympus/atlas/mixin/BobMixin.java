package com.github.olympus.atlas.mixin;

import com.github.olympus.atlas.client.Projection;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class BobMixin {
    @Inject(method = "bobView", at = @At("TAIL"))
    private void atlas$bob(PoseStack pose, float partialTick, CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        Projection.sway(pose.last().pose(), minecraft.getWindow().getWidth()
                / (double) Math.max(1, minecraft.getWindow().getHeight()));
    }
}
