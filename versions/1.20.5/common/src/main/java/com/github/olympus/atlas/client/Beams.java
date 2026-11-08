package com.github.olympus.atlas.client;

import com.github.olympus.atlas.core.render.BeamShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

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
            Matrix4f matrix = pose.last().pose();
            PoseStack.Pose normal = pose.last();
            VertexConsumer core = buffers.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION,
                    !beam.solid()));
            BeamShape.core(sink(core, matrix, normal, beam.core()), time);
            VertexConsumer glow = buffers.getBuffer(RenderType.beaconBeam(BeaconRenderer.BEAM_LOCATION, true));
            BeamShape.glow(sink(glow, matrix, normal, beam.glow()), time);
            pose.popPose();
        }
    }

    private static BeamShape.Sink sink(VertexConsumer consumer, Matrix4f matrix, PoseStack.Pose normal, int color) {
        int a = color >>> 24;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (x, y, z, u, v) -> consumer.vertex(matrix, x, y, z).color(r, g, b, a).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LIGHT).normal(normal, 0f, 1f, 0f).endVertex();
    }
}
