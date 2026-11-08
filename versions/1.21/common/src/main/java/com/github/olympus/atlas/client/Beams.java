package com.github.olympus.atlas.client;

import com.github.olympus.atlas.core.render.BeamShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class Beams {
    private static final int LIGHT = 15728880;

    private Beams() {
    }

    public static void draw(PoseStack pose, MultiBufferSource buffers, double x, double y, double z) {
        List<WorldBeams.Beam> beams = WorldBeams.collect(x, y, z);
        if (beams.isEmpty()) {
            return;
        }
        float time = WorldBeams.time();
        for (WorldBeams.Beam beam : beams) {
            pose.pushPose();
            pose.translate(beam.x(), beam.y(), beam.z());
            PoseStack.Pose last = pose.last();
            VertexConsumer core = buffers.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION,
                    !beam.solid()));
            BeamShape.core(sink(core, last, beam.core()), time);
            VertexConsumer glow = buffers.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
            BeamShape.glow(sink(glow, last, beam.glow()), time);
            pose.popPose();
        }
    }

    private static BeamShape.Sink sink(VertexConsumer consumer, PoseStack.Pose pose, int color) {
        return (x, y, z, u, v) -> consumer.addVertex(pose, x, y, z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(pose, 0f, 1f, 0f);
    }
}
