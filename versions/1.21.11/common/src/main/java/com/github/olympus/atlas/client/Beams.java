package com.github.olympus.atlas.client;

import com.github.olympus.atlas.core.render.BeamShape;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class Beams {
    private static final int LIGHT = 15728880;

    private Beams() {
    }

    public static void draw(PoseStack pose, SubmitNodeCollector collector, double x, double y, double z) {
        List<WorldBeams.Beam> beams = WorldBeams.collect(x, y, z);
        if (beams.isEmpty()) {
            return;
        }
        float time = WorldBeams.time();
        for (WorldBeams.Beam beam : beams) {
            int core = beam.core();
            int glow = beam.glow();
            pose.pushPose();
            pose.translate(beam.x(), beam.y(), beam.z());
            collector.submitCustomGeometry(pose, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, !beam.solid()),
                    (last, consumer) -> BeamShape.core(sink(consumer, last, core), time));
            collector.submitCustomGeometry(pose, RenderTypes.beaconBeam(BeaconRenderer.BEAM_LOCATION, true),
                    (last, consumer) -> BeamShape.glow(sink(consumer, last, glow), time));
            pose.popPose();
        }
    }

    private static BeamShape.Sink sink(VertexConsumer consumer, PoseStack.Pose pose, int color) {
        return (x, y, z, u, v) -> consumer.addVertex(pose, x, y, z).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(pose, 0f, 1f, 0f);
    }
}
