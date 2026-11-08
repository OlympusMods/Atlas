package com.github.olympus.atlas.mixin;

import com.github.olympus.atlas.client.Beams;
import com.github.olympus.atlas.client.Cam;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class BeamMixin {
    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Inject(method = "renderEntities", at = @At("TAIL"))
    private void atlas$beams(CallbackInfo callback) {
        Beams.draw(new PoseStack(), renderBuffers.bufferSource(), Cam.x(), Cam.y(), Cam.z());
    }
}
