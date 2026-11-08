package com.github.olympus.atlas.client.ui;

import com.github.olympus.atlas.client.Atlas;
import com.github.olympus.atlas.client.Cam;
import com.github.olympus.atlas.client.Compat;
import com.github.olympus.atlas.client.MapTexture;
import com.github.olympus.atlas.client.Projection;
import com.github.olympus.atlas.client.World;
import com.github.olympus.atlas.core.AtlasConfig;
import com.github.olympus.atlas.core.map.MapStore;
import com.github.olympus.atlas.core.map.Region;
import com.github.olympus.atlas.core.render.AsyncFrame;
import com.github.olympus.atlas.core.render.Camera;
import com.github.olympus.atlas.core.render.MapPainter;
import com.github.olympus.atlas.core.render.Marker;
import com.github.olympus.atlas.core.render.MinimapPainter;
import com.github.olympus.atlas.core.render.TerrainRenderer;
import com.github.olympus.atlas.core.waypoint.Icons;
import com.github.olympus.atlas.core.waypoint.Waypoint;
import com.github.olympus.atlas.core.waypoint.WaypointStore;
import com.github.olympus.proteus.client.ui.Canvas;
import com.github.olympus.proteus.client.ui.Overlay;
import com.github.olympus.proteus.client.ui.Pixels;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class HudOverlay implements Overlay {
    private static final int BACKGROUND = 0xD0101318;
    private static final int[] BLIP_COLORS = {0xFFFFFF, 0xFF5A52, 0x7CD66B};

    private record Params(MapStore store, MapPainter.View view, TerrainRenderer.Scene scene, List<Marker> markers,
            boolean round, int opacity) {
    }

    private final MapTexture texture = new MapTexture("minimap");
    private AsyncFrame<Params> frames;
    private long shownSerial = -1;
    private final double[] point = new double[3];

    @Override
    public boolean visible() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.player != null && Atlas.world() != null;
    }

    @Override
    public void render(Canvas canvas) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        AtlasConfig config = Atlas.config();
        Screen screen = Compat.screen(minecraft);
        boolean covered = screen != null && !(screen instanceof ChatScreen);
        float partial = Atlas.partial();
        if (config.waypointsInWorld && Atlas.showWaypoints() && !covered) {
            waypoints(canvas, config, player, partial);
        }
        if (config.minimapEnabled && Atlas.minimapAllowed() && !(covered && config.minimapHideInScreens)) {
            minimap(canvas, minecraft, config, player, partial);
        }
    }

    private AsyncFrame<Params> frames() {
        if (frames == null) {
            frames = new AsyncFrame<>(Atlas.workers(), 1, false, this::paint, null);
        }
        return frames;
    }

    private void paint(Params params, AsyncFrame.Frame<Params> frame, int rowFrom, int rowTo) {
        if (params.scene() != null) {
            new TerrainRenderer(params.store()).paint(params.scene(), frame.pixels, null, frame.width, frame.height,
                    rowFrom, rowTo);
        } else {
            MapPainter.paint(params.store(), params.view(), frame.pixels, frame.width, frame.height, rowFrom, rowTo);
        }
        MinimapPainter.markers(frame.pixels, frame.width, frame.height, params.markers());
        MinimapPainter.frame(frame.pixels, frame.width, params.round(), params.opacity(), 0xFF1B1F27,
                Math.max(2f, frame.width / 60f));
        MapTexture.abgr(frame.pixels, 0, frame.pixels.length);
    }

    private void minimap(Canvas canvas, Minecraft minecraft, AtlasConfig config, LocalPlayer player, float partial) {
        MapStore store = Atlas.store();
        if (store == null) {
            return;
        }
        double guiScale = minecraft.getWindow().getGuiScale();
        int size = config.minimapSize;
        int pixels = Math.max(32, (int) Math.round(size * guiScale));
        double px = World.x(player, partial);
        double py = World.y(player, partial);
        double pz = World.z(player, partial);
        float yaw = World.yaw(player, partial);
        boolean round = config.minimapShape == AtlasConfig.Shape.ROUND;
        double scale = config.minimapScale() * guiScale;
        double rotation = config.minimapRotate ? Math.toRadians(yaw + 180.0) : 0.0;
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double half = pixels / 2.0;
        double reach = half - Math.max(4.0, pixels / 28.0);
        List<Marker> markers = new ArrayList<>();
        TerrainRenderer.Scene scene = null;
        Camera camera = null;
        if (config.minimapTilt) {
            double span = pixels / scale;
            camera = Camera.orbit(px, py, pz, config.minimapRotate ? yaw : 180.0, 48.0, span * 1.25, 50.0, 1.0);
            scene = new TerrainRenderer.Scene(camera, 1.0, span * 6.0, true, 0x1A2030, 0x39445A, 400);
        }
        float dot = (float) Math.max(1.5, pixels / 70.0);
        if (config.minimapWaypoints) {
            WaypointStore waypoints = Atlas.waypoints();
            for (Waypoint waypoint : waypoints.in(Atlas.dimension())) {
                if (!waypoints.shown(waypoint)) {
                    continue;
                }
                if (place(camera, waypoint.x + 0.5 - px, waypoint.y, waypoint.z + 0.5 - pz, px, pz, cos, sin, scale,
                        half, reach, round, true)) {
                    markers.add(new Marker((float) point[0], (float) point[1], waypoint.color, Marker.DIAMOND,
                            dot * 1.9f, 0f));
                }
            }
        }
        for (World.Blip blip : Atlas.blips()) {
            double ex = World.x(blip.entity(), partial);
            double ez = World.z(blip.entity(), partial);
            if (place(camera, ex - px, World.y(blip.entity(), partial), ez - pz, px, pz, cos, sin, scale, half,
                    reach, round, false)) {
                markers.add(new Marker((float) point[0], (float) point[1], BLIP_COLORS[blip.kind()],
                        blip.kind() == World.PLAYER ? Marker.SQUARE : Marker.DOT, dot, 0f));
            }
        }
        float heading = config.minimapRotate ? 0f : (float) Math.toRadians(yaw + 180.0);
        if (camera != null && place(camera, 0, py, 0, px, pz, cos, sin, scale, half, reach, round, false)) {
            markers.add(new Marker((float) point[0], (float) point[1], 0xFFFFFF, Marker.ARROW, dot * 3.2f, heading));
        } else if (camera == null) {
            markers.add(new Marker((float) half, (float) half, 0xFFFFFF, Marker.ARROW, dot * 3.2f, heading));
        }
        MapPainter.View view = new MapPainter.View(px, pz, scale, rotation, BACKGROUND, false);
        frames().request(new Params(store, view, scene, markers, round, config.minimapOpacity), pixels, pixels);
        AsyncFrame.Frame<Params> frame = frames().take();
        if (frame != null && frame.serial != shownSerial && frame.width == frame.height) {
            shownSerial = frame.serial;
            texture.upload(frame.pixels, frame.width, frame.height);
        }
        int left = switch (config.minimapCorner) {
            case TOP_LEFT, BOTTOM_LEFT -> config.minimapOffsetX;
            default -> canvas.width() - size - config.minimapOffsetX;
        };
        List<String> lines = info(minecraft, config, player);
        int textHeight = lines.size() * (canvas.lineHeight() + 1);
        int top = switch (config.minimapCorner) {
            case TOP_LEFT, TOP_RIGHT -> config.minimapOffsetY;
            default -> canvas.height() - size - config.minimapOffsetY - textHeight - (lines.isEmpty() ? 0 : 3);
        };
        if (!texture.ready()) {
            return;
        }
        texture.draw(canvas, left, top, size, size);
        if (config.minimapCompass && !config.minimapTilt) {
            compass(canvas, left + size / 2f, top + size / 2f, size / 2f - 7f, cos, sin, round);
        }
        int y = top + size + 3;
        for (String line : lines) {
            int width = canvas.textWidth(line);
            canvas.text(line, left + (size - width) / 2, y, 0xFFFFFFFF, true);
            y += canvas.lineHeight() + 1;
        }
    }

    private boolean place(Camera camera, double ex, double wy, double ez, double px, double pz, double cos,
            double sin, double scale, double half, double reach, boolean round, boolean clamp) {
        double x;
        double y;
        if (camera != null) {
            if (!camera.project(px + ex, wy, pz + ez, half * 2.0, half * 2.0, point)) {
                return false;
            }
            x = point[0] - half;
            y = point[1] - half;
        } else {
            x = (ex * cos + ez * sin) * scale;
            y = (-ex * sin + ez * cos) * scale;
        }
        double distance = round ? Math.sqrt(x * x + y * y) : Math.max(Math.abs(x), Math.abs(y));
        if (distance > reach) {
            if (!clamp) {
                return false;
            }
            x *= reach / distance;
            y *= reach / distance;
        }
        point[0] = half + x;
        point[1] = half + y;
        return true;
    }

    private static void compass(Canvas canvas, float cx, float cy, float radius, double cos, double sin,
            boolean round) {
        String[] letters = {"N", "E", "S", "W"};
        double[][] directions = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            double x = directions[i][0] * cos + directions[i][1] * sin;
            double y = -directions[i][0] * sin + directions[i][1] * cos;
            if (!round) {
                double stretch = 1.0 / Math.max(Math.abs(x), Math.abs(y));
                x *= stretch;
                y *= stretch;
            }
            int tx = Math.round(cx + (float) x * radius) - canvas.textWidth(letters[i]) / 2;
            int ty = Math.round(cy + (float) y * radius) - canvas.lineHeight() / 2 + 1;
            canvas.text(letters[i], tx, ty, i == 0 ? 0xFFFF6B5E : 0xFFE8ECF2, true);
        }
    }

    private static List<String> info(Minecraft minecraft, AtlasConfig config, LocalPlayer player) {
        List<String> lines = new ArrayList<>();
        BlockPos pos = player.blockPosition();
        if (config.minimapCoords) {
            lines.add(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        }
        if (config.minimapBiome) {
            String biome = World.biomeAt(minecraft.level, pos);
            if (!biome.isEmpty()) {
                lines.add(pretty(biome));
            }
        }
        if (config.minimapLight) {
            lines.add(Atlas.text("atlas.hud.light", World.blockLight(minecraft.level, pos),
                    World.skyLight(minecraft.level, pos)));
        }
        return lines;
    }

    public static String pretty(String id) {
        String name = id.substring(id.indexOf(':') + 1).replace('_', ' ').replace('/', ' ');
        StringBuilder text = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            text.append(upper ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return text.toString();
    }

    private void waypoints(Canvas canvas, AtlasConfig config, LocalPlayer player, float partial) {
        if (!Cam.ready()) {
            return;
        }
        Cam.prepare();
        WaypointStore store = Atlas.waypoints();
        List<Waypoint> list = store.in(Atlas.dimension());
        if (list.isEmpty()) {
            return;
        }
        int width = canvas.width();
        int height = canvas.height();
        Camera camera = new Camera(Cam.x(), Cam.y(), Cam.z(), Cam.yaw(), Cam.pitch(), Projection.fov(),
                width / (double) height);
        camera.post = Projection.sway();
        double px = World.x(player, partial);
        double py = World.y(player, partial);
        double pz = World.z(player, partial);
        float base = config.waypointScale / 100f;
        Waypoint focused = null;
        double focusedOffset = 22.0;
        float focusedX = 0;
        float focusedY = 0;
        float focusedAlpha = 0;
        float focusedScale = 1f;
        double focusedDistance = 0;
        for (Waypoint waypoint : list) {
            if (!store.shown(waypoint)) {
                continue;
            }
            double distance = waypoint.distance(px, py, pz);
            float alpha = 1f;
            if (config.waypointMaxDistance > 0) {
                if (distance > config.waypointMaxDistance) {
                    continue;
                }
                double fade = config.waypointMaxDistance * 0.8;
                if (distance > fade) {
                    alpha = (float) (1.0 - (distance - fade) / (config.waypointMaxDistance - fade));
                }
            }
            if (Atlas.occluded(waypoint)) {
                if (config.waypointOcclusion == AtlasConfig.Occlusion.HIDE) {
                    continue;
                }
                alpha *= 0.35f;
            }
            if (distance < 2.5) {
                alpha *= (float) Math.max(0.0, (distance - 1.0) / 1.5);
            }
            if (alpha <= 0.04f) {
                continue;
            }
            if (!camera.project(waypoint.x + 0.5, waypoint.y + 0.5, waypoint.z + 0.5, width, height, point)) {
                continue;
            }
            float sx = (float) point[0];
            float sy = (float) point[1];
            if (sx < -40 || sy < -40 || sx > width + 40 || sy > height + 40) {
                continue;
            }
            float scale = base * (float) Math.max(0.5, Math.min(1.7, 0.5 + 1.2 * (1.0 - (distance - 5.0) / 45.0)));
            icon(canvas, waypoint, sx, sy, scale, alpha);
            double offset = Math.hypot(sx - width / 2.0, sy - height / 2.0);
            if (offset < focusedOffset) {
                focusedOffset = offset;
                focused = waypoint;
                focusedX = sx;
                focusedY = sy;
                focusedAlpha = alpha;
                focusedScale = scale;
                focusedDistance = distance;
            }
            if (config.waypointNames || config.waypointDistance) {
                label(canvas, config, waypoint, sx, sy, Math.max(0.6f, scale * 0.7f), alpha, distance, false);
            }
        }
        if (focused != null && !(config.waypointNames && config.waypointDistance)) {
            label(canvas, config, focused, focusedX, focusedY, Math.max(0.75f, focusedScale * 0.7f), focusedAlpha,
                    focusedDistance, true);
        }
    }

    private static void label(Canvas canvas, AtlasConfig config, Waypoint waypoint, float sx, float sy, float scale,
            float alpha, double distance, boolean full) {
        int a = Math.max(0x30, Math.min(255, (int) (alpha * 255f)));
        canvas.push();
        canvas.translate(sx, sy + 10f * scale);
        canvas.scale(scale, scale);
        int y = 0;
        if (config.waypointNames || full) {
            String name = waypoint.name.isEmpty() ? Atlas.text("atlas.waypoint.unnamed") : waypoint.name;
            int w = canvas.textWidth(name);
            canvas.fill(-w / 2 - 2, y - 1, w + 4, canvas.lineHeight() + 1, (a / 3) << 24);
            canvas.text(name, -w / 2, y, (a << 24) | 0xFFFFFF, false);
            y += canvas.lineHeight() + 1;
        }
        if (config.waypointDistance || full) {
            String text = distance(distance);
            int w = canvas.textWidth(text);
            canvas.text(text, -w / 2, y, (a << 24) | 0xC8D0DA, true);
        }
        canvas.pop();
    }

    public static String distance(double blocks) {
        if (blocks >= 10000.0) {
            return String.format(Locale.ROOT, "%.1f km", blocks / 1000.0);
        }
        return Math.round(blocks) + " m";
    }

    public static void icon(Canvas canvas, Waypoint waypoint, float x, float y, float scale, float alpha) {
        int a = Math.max(0x30, Math.min(255, (int) (alpha * 255f)));
        int back = ((a * 3 / 4) << 24) | 0x10131A;
        canvas.push();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        canvas.translate(-0.5f, -0.5f);
        canvas.fill(-4, -5, 9, 11, back);
        canvas.fill(-5, -4, 1, 9, back);
        canvas.fill(5, -4, 1, 9, back);
        Pixels.draw(canvas, 0, 0, (a << 24) | waypoint.color, Icons.rows(waypoint.icon));
        canvas.pop();
    }
}
