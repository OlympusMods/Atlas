package com.github.olympus.atlas.client;

import com.github.olympus.atlas.core.AtlasConfig;
import com.github.olympus.atlas.core.render.BeamShape;
import com.github.olympus.atlas.core.waypoint.Waypoint;
import com.github.olympus.atlas.core.waypoint.WaypointStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class WorldBeams {
    public record Beam(double x, double y, double z, int color, float alpha) {
        public boolean solid() {
            return alpha >= 0.999f;
        }

        public int core() {
            return (Math.round(alpha * 255f) << 24) | color;
        }

        public int glow() {
            return (Math.round(alpha * BeamShape.GLOW_ALPHA * 255f) << 24) | color;
        }
    }

    private static final List<Beam> OUT = new ArrayList<>();

    private WorldBeams() {
    }

    public static float time() {
        return (System.nanoTime() / 1000000L % 2000L) / 50f;
    }

    public static List<Beam> collect(double camX, double camY, double camZ) {
        OUT.clear();
        AtlasConfig config = Atlas.config();
        WaypointStore store = Atlas.waypoints();
        Minecraft minecraft = Minecraft.getInstance();
        if (store == null || minecraft.level == null || !config.waypointsInWorld || !config.waypointBeams
                || !Atlas.showWaypoints()) {
            return OUT;
        }
        double reach = Math.max(48.0, World.renderDistance(minecraft) * 16.0 - 32.0);
        for (Waypoint waypoint : store.in(Atlas.dimension())) {
            if (!waypoint.beam || !store.shown(waypoint)) {
                continue;
            }
            double dx = waypoint.x + 0.5 - camX;
            double dy = waypoint.y - camY;
            double dz = waypoint.z + 0.5 - camZ;
            double flat = Math.sqrt(dx * dx + dz * dz);
            float alpha = BeamShape.fade(flat, Math.sqrt(flat * flat + dy * dy), config.waypointMaxDistance);
            if (alpha <= 0.02f) {
                continue;
            }
            if (flat > reach) {
                double scale = reach / flat;
                dx *= scale;
                dy *= scale;
                dz *= scale;
            }
            OUT.add(new Beam(dx, dy, dz, waypoint.color & 0xFFFFFF, alpha));
        }
        return OUT;
    }
}
