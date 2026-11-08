package com.github.olympus.atlas.mixin;

import com.github.olympus.atlas.client.Beams;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class BeamMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void atlas$beams(PoseStack pose, LevelRenderState state, SubmitNodeCollector collector,
            CallbackInfo callback) {
        Vec3 eye = state.cameraRenderState.pos;
        Beams.draw(pose, collector, eye.x, eye.y, eye.z);
    }
}
