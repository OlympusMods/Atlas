package com.github.olympus.atlas.mixin;

import com.github.olympus.atlas.client.Projection;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameRenderer.class, priority = 1500)
public abstract class FovMixin {
    @Inject(method = "getFov", at = @At("RETURN"))
    private void atlas$fov(Camera camera, float partialTick, boolean world, CallbackInfoReturnable<Float> callback) {
        if (world) {
            Projection.fov(callback.getReturnValue());
        }
    }
}
