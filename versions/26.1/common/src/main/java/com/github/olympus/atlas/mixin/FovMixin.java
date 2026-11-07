package com.github.olympus.atlas.mixin;

import com.github.olympus.atlas.client.Projection;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Camera.class, priority = 1500)
public abstract class FovMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"))
    private void atlas$fov(float partialTick, CallbackInfoReturnable<Float> callback) {
        Projection.fov(callback.getReturnValue());
    }
}
