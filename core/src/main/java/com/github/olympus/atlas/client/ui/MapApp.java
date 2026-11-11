package com.github.olympus.atlas.client.ui;

import com.github.olympus.atlas.client.Atlas;
import com.github.olympus.atlas.client.AtlasClient;
import com.github.olympus.atlas.client.AtlasMenu;
import com.github.olympus.atlas.client.Compat;
import com.github.olympus.atlas.client.KeyBindings;
import com.github.olympus.atlas.client.MapTexture;
import com.github.olympus.atlas.client.PageLayer;
import com.github.olympus.atlas.client.World;
import com.github.olympus.atlas.core.AtlasConfig;
import com.github.olympus.atlas.core.map.MapStore;
import com.github.olympus.atlas.core.map.MapWorld;
import com.github.olympus.atlas.core.map.Region;
import com.github.olympus.atlas.core.render.AsyncFrame;
import com.github.olympus.atlas.core.render.Camera;
import com.github.olympus.atlas.core.render.MapPainter;
import com.github.olympus.atlas.core.render.TerrainRenderer;
import com.github.olympus.atlas.core.waypoint.Icons;
import com.github.olympus.atlas.core.waypoint.Importers;
import com.github.olympus.atlas.core.waypoint.Waypoint;
import com.github.olympus.atlas.core.waypoint.WaypointStore;
import com.github.olympus.proteus.client.ui.ButtonKind;
import com.github.olympus.proteus.client.ui.Canvas;
import com.github.olympus.proteus.client.ui.Draw;
import com.github.olympus.proteus.client.ui.Pixels;
import com.github.olympus.proteus.client.ui.RawInput;
import com.github.olympus.proteus.client.ui.RawKey;
import com.github.olympus.proteus.client.ui.Theme;
import com.github.olympus.proteus.client.ui.ToastOverlay;
import com.github.olympus.proteus.client.ui.UiHost;
import com.github.olympus.proteus.client.ui.UiShell;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class MapApp extends UiShell implements RawInput {
    private static final int BACKGROUND = 0xFF12151B;
    private static final int BAR = 20;
    private static final int LIST = 176;

    private record Params(MapStore store, MapPainter.View view, TerrainRenderer.Scene scene) {
    }

    private final MapTexture texture = new MapTexture("fullscreen");
    private final AsyncFrame<Params> frames;
    private final PageLayer pages;
    private final double[] point = new double[3];
    private final List<int[]> blocked = new ArrayList<>();

    private double centerX;
    private double centerZ;
    private double scale;
    private double targetScale;
    private double anchorX;
    private double anchorZ;
    private int anchorMouseX;
    private int anchorMouseY;
    private boolean terrain;
    private double yaw = 200.0;
    private double pitch = 42.0;
    private double ground = 64.0;
    private String dimension;
    private boolean cave;
    private int layer;
    private boolean grid;
    private boolean list;
    private double guiScale = 2.0;

    private int mouseX;
    private int mouseY;
    private int lastMouseX;
    private int lastMouseY;
    private int dragButton = -1;
    private double dragged;
    private long lastClick;
    private double[] lastState = new double[0];
    private double lastRequest = -1.0;
    private long shownSerial = -1;
    private Params shown;
    private float[] shownDepth;

    private Waypoint editing;
    private boolean editingNew;
    private boolean menu;
    private int menuX;
    private int menuY;
    private int menuWorldX;
    private int menuWorldZ;
    private Waypoint menuTarget;
    private List<Importers.Source> sources;

    public MapApp(UiHost host) {
        super(host);
        Minecraft minecraft = Minecraft.getInstance();
        frames = new AsyncFrame<>(Atlas.workers(), Atlas.threads(), true, this::paint,
                (params, frame) -> MapTexture.abgr(frame.pixels, 0, frame.pixels.length));
        pages = new PageLayer(Atlas.workers(), Atlas.threads());
        AtlasConfig config = Atlas.config();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            centerX = player.getX();
            centerZ = player.getZ();
            ground = player.getY();
        }
        guiScale = minecraft.getWindow().getGuiScale();
        scale = 2.0 * guiScale;
        targetScale = scale;
        dimension = Atlas.dimension();
        cave = Atlas.caveActive();
        layer = MapWorld.layer(player == null ? 64 : player.blockPosition().getY());
        grid = config.mapGrid;
        list = config.mapWaypointList;
    }

    private void paint(Params params, AsyncFrame.Frame<Params> frame, int rowFrom, int rowTo) {
        if (params.scene() != null) {
            new TerrainRenderer(params.store()).paint(params.scene(), frame.pixels, frame.depth, frame.width,
                    frame.height, rowFrom, rowTo);
        } else {
            MapPainter.paint(params.store(), params.view(), frame.pixels, frame.width, frame.height, rowFrom, rowTo);
        }
    }

    @Override
    public void removed() {
        frames.clear();
        texture.close();
        pages.close();
        AtlasConfig config = Atlas.config();
        if (config.mapGrid != grid || config.mapWaypointList != list) {
            config.mapGrid = grid;
            config.mapWaypointList = list;
            Atlas.save();
        }
    }

    @Override
    public boolean vanillaBackdrop(boolean inWorld) {
        return false;
    }

    @Override
    public void render(Canvas target, int x, int y) {
        mouseX = x;
        mouseY = y;
        super.render(target, x, y);
        lastMouseX = x;
        lastMouseY = y;
    }

    private boolean modal() {
        return editing != null || sources != null;
    }

    private boolean overMap() {
        if (modal() || menu) {
            return false;
        }
        for (int[] rect : blocked) {
            if (mouseX >= rect[0] && mouseY >= rect[1] && mouseX < rect[0] + rect[2]
                    && mouseY < rect[1] + rect[3]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void mousePressed(int button) {
        if (menu && !inside(menuX, menuY, 130, 100)) {
            menu = false;
        } else if (overMap() && (button == 0 || button == 1)) {
            dragButton = button;
            dragged = 0.0;
            gui().blur();
        }
        super.mousePressed(button);
    }

    private boolean inside(int x, int y, int w, int h) {
        return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
    }

    @Override
    public void mouseReleased(int button) {
        if (button == dragButton) {
            dragButton = -1;
            if (dragged < 3.0) {
                clicked(button);
            }
        }
        super.mouseReleased(button);
    }

    @Override
    public void mouseScrolled(double amount) {
        if (!overMap()) {
            super.mouseScrolled(amount);
            return;
        }
        zoom(Math.pow(1.25, amount), mouseX, mouseY);
    }

    private void zoom(double factor, int x, int y) {
        anchorX = worldX(x);
        anchorZ = worldZ(y);
        anchorMouseX = x;
        anchorMouseY = y;
        targetScale = Math.max(0.02 * guiScale, Math.min(16.0 * guiScale, targetScale * factor));
        if (!Atlas.config().mapSmoothZoom || terrain) {
            settle();
        }
    }

    private void settle() {
        scale = targetScale;
        if (!terrain) {
            centerX = anchorX - (anchorMouseX - width() / 2.0) * guiScale / scale;
            centerZ = anchorZ - (anchorMouseY - height() / 2.0) * guiScale / scale;
        }
    }

    private double worldX(int x) {
        return centerX + (x - width() / 2.0) * guiScale / scale;
    }

    private double worldZ(int y) {
        return centerZ + (y - height() / 2.0) * guiScale / scale;
    }

    @Override
    public boolean rawKey(RawKey key) {
        if (gui().typing() || key.escape() || key.key() == null) {
            return false;
        }
        String name = key.key().getName();
        KeyBindings keys = AtlasClient.keys();
        if (Atlas.matches(keys.view, name)) {
            toggleTerrain();
            return true;
        }
        if (Atlas.matches(keys.map, name)) {
            requestClose();
            return true;
        }
        if (modal()) {
            return false;
        }
        if (Atlas.matches(keys.zoomIn, name)) {
            zoom(1.5, width() / 2, height() / 2);
            return true;
        }
        if (Atlas.matches(keys.zoomOut, name)) {
            zoom(1.0 / 1.5, width() / 2, height() / 2);
            return true;
        }
        if (Atlas.matches(keys.cave, name)) {
            cave = !cave && Atlas.cavesAllowed();
            return true;
        }
        if (Atlas.matches(keys.waypoint, name)) {
            create((int) Math.floor(centerX), (int) Math.floor(centerZ));
            return true;
        }
        return false;
    }

    @Override
    protected boolean onEscape() {
        if (menu) {
            menu = false;
            return true;
        }
        if (editing != null) {
            editing = null;
            return true;
        }
        if (sources != null) {
            sources = null;
            return true;
        }
        return false;
    }

    private void toggleTerrain() {
        terrain = !terrain;
        targetScale = scale;
        MapStore store = store();
        int top = store == null ? Region.NONE : store.height((int) Math.floor(centerX), (int) Math.floor(centerZ));
        if (terrain && top != Region.NONE) {
            ground = top;
        }
        lastState = new double[0];
        shownSerial = -1;
    }

    private MapStore store() {
        boolean allowed = cave && Atlas.cavesAllowed();
        return Atlas.store(dimension, allowed ? layer : MapWorld.SURFACE);
    }

    private double distance() {
        AtlasConfig config = Atlas.config();
        double tan = Math.tan(Math.toRadians(config.terrainFov) / 2.0);
        double aspect = width() / (double) Math.max(1, height());
        return width() * guiScale / scale / (2.0 * tan * aspect);
    }

    private Camera camera() {
        AtlasConfig config = Atlas.config();
        return Camera.orbit(centerX, ground * config.terrainVertical, centerZ, yaw, pitch, distance(),
                config.terrainFov, width() / (double) Math.max(1, height()));
    }

    private void clicked(int button) {
        Waypoint hit = waypointAt(mouseX, mouseY);
        if (button == 0) {
            long now = System.currentTimeMillis();
            boolean twice = now - lastClick < 350;
            lastClick = now;
            if (hit != null) {
                edit(hit, false);
            } else if (twice && !terrain) {
                create((int) Math.floor(worldX(mouseX)), (int) Math.floor(worldZ(mouseY)));
            }
        } else if (!terrain || hit != null) {
            menu = true;
            menuX = Math.min(mouseX, width() - 134);
            menuY = Math.min(mouseY, height() - 104);
            menuWorldX = (int) Math.floor(worldX(mouseX));
            menuWorldZ = (int) Math.floor(worldZ(mouseY));
            menuTarget = hit;
        }
    }

    private void create(int x, int z) {
        Waypoint waypoint = new Waypoint();
        waypoint.x = x;
        waypoint.z = z;
        waypoint.y = surface(x, z);
        waypoint.dimension = dimension;
        int number = Atlas.waypoints().all().size() + 1;
        waypoint.name = Atlas.text("atlas.waypoint.numbered", number);
        waypoint.color = Waypoint.COLORS[number % Waypoint.COLORS.length];
        edit(waypoint, true);
    }

    private int surface(int x, int z) {
        MapStore store = store();
        int top = store == null ? Region.NONE : store.height(x, z);
        if (top != Region.NONE) {
            return top + 1;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 64 : player.blockPosition().getY();
    }

    private void edit(Waypoint waypoint, boolean fresh) {
        editing = waypoint.copy();
        editingNew = fresh;
        menu = false;
        gui().setField("wp-name", editing.name);
        gui().setField("wp-group", editing.group);
        gui().setField("wp-x", Integer.toString(editing.x));
        gui().setField("wp-y", Integer.toString(editing.y));
        gui().setField("wp-z", Integer.toString(editing.z));
        gui().focus("wp-name");
    }

    private Waypoint waypointAt(int x, int y) {
        WaypointStore store = Atlas.waypoints();
        Waypoint best = null;
        double bestDistance = 9.0;
        for (Waypoint waypoint : store.in(dimension)) {
            if (shown == null || !store.shown(waypoint) || !place(waypoint.x + 0.5, waypoint.y + 1.0, waypoint.z + 0.5,
                    width(), height())) {
                continue;
            }
            double distance = Math.hypot(point[0] - x, point[1] - y);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = waypoint;
            }
        }
        return best;
    }

    @Override
    protected void renderContent(Theme theme) {
        Minecraft minecraft = Minecraft.getInstance();
        Canvas canvas = fade();
        AtlasConfig config = Atlas.config();
        int width = width();
        int height = height();
        guiScale = minecraft.getWindow().getGuiScale();
        drag();
        if (Math.abs(targetScale - scale) > scale * 0.002) {
            scale += (targetScale - scale) * 0.3;
            if (!terrain) {
                centerX = anchorX - (anchorMouseX - width / 2.0) * guiScale / scale;
                centerZ = anchorZ - (anchorMouseY - height / 2.0) * guiScale / scale;
            }
        } else {
            scale = targetScale;
        }
        MapStore store = store();
        boolean paged = !terrain && config.mapGpu && pages.usable();
        canvas.fill(0, 0, width, height, BACKGROUND);
        if (paged) {
            shown = store == null ? null : new Params(store, new MapPainter.View(centerX, centerZ, scale, 0.0,
                    BACKGROUND, grid), null);
            shownRatio = guiScale;
            shownDepth = null;
            shownSerial = -1;
            if (store != null) {
                pages.draw(canvas, store, centerX, centerZ, scale, width, height, guiScale, BACKGROUND);
                if (grid) {
                    gridLines(canvas, width, height);
                }
            }
        } else {
            if (store != null) {
                request(store, config, width, height);
            }
            AsyncFrame.Frame<Params> frame = frames.take();
            if (frame != null && frame.serial != shownSerial) {
                shownSerial = frame.serial;
                shown = frame.params;
                shownRatio = frame.width / (double) Math.max(1, width);
                shownDepth = frame.depth;
                depthWidth = frame.width;
                depthHeight = frame.height;
                texture.upload(frame.pixels, frame.width, frame.height);
            }
            texture.draw(canvas, 0, 0, width, height);
        }
        markers(canvas, minecraft, width, height);
        blocked.clear();
        if (!modal()) {
            toolbar(theme, canvas, width);
            if (list) {
                panel(theme, canvas, minecraft, width, height);
            }
            status(canvas, store, width, height);
            if (menu) {
                contextMenu(theme, canvas, minecraft);
            }
        } else {
            canvas.fill(0, 0, width, height, 0x90000000);
            if (editing != null) {
                editor(theme, canvas, minecraft, width, height);
            } else {
                importer(theme, canvas, width, height);
            }
        }
    }

    private double shownRatio = 1.0;
    private int depthWidth = 1;
    private int depthHeight = 1;

    private void gridLines(Canvas canvas, int width, int height) {
        int step = scale >= 0.75 ? 16 : scale >= 0.04 ? Region.SIZE : 0;
        if (step == 0) {
            return;
        }
        double screenWidth = width * guiScale;
        double screenHeight = height * guiScale;
        int right = (int) Math.ceil(screenWidth);
        int bottom = (int) Math.ceil(screenHeight);
        float unit = (float) (1.0 / guiScale);
        canvas.push();
        canvas.scale(unit, unit);
        for (double x = Math.ceil((centerX - screenWidth / 2.0 / scale) / step) * step;; x += step) {
            int at = (int) Math.round((x - centerX) * scale + screenWidth / 2.0);
            if (at >= right) {
                break;
            }
            canvas.fill(at, 0, 1, bottom, 0x40000000);
        }
        for (double z = Math.ceil((centerZ - screenHeight / 2.0 / scale) / step) * step;; z += step) {
            int at = (int) Math.round((z - centerZ) * scale + screenHeight / 2.0);
            if (at >= bottom) {
                break;
            }
            canvas.fill(0, at, right, 1, 0x40000000);
        }
        canvas.pop();
    }

    private void drag() {
        if (dragButton < 0) {
            return;
        }
        int dx = mouseX - lastMouseX;
        int dy = mouseY - lastMouseY;
        dragged += Math.abs(dx) + Math.abs(dy);
        if (dx == 0 && dy == 0) {
            return;
        }
        if (!terrain) {
            if (dragButton == 0) {
                centerX -= dx * guiScale / scale;
                centerZ -= dy * guiScale / scale;
                targetScale = scale;
            }
            return;
        }
        if (dragButton == 0) {
            yaw += dx * 0.4;
            pitch = Math.max(8.0, Math.min(89.0, pitch + dy * 0.3));
            return;
        }
        Camera camera = camera();
        double step = distance() * 2.0 * camera.tanHalf / Math.max(1, height());
        double length = Math.hypot(camera.fx, camera.fz);
        double fx = length < 1.0e-6 ? -camera.uz : camera.fx / length;
        double fz = length < 1.0e-6 ? camera.ux : camera.fz / length;
        double forward = step / Math.max(0.25, Math.sin(Math.toRadians(pitch)));
        centerX += -camera.rx * dx * step + fx * dy * forward;
        centerZ += -camera.rz * dx * step + fz * dy * forward;
    }

    private void request(MapStore store, AtlasConfig config, int width, int height) {
        double pixelsWide = width * guiScale;
        double pixelsHigh = height * guiScale;
        double budget = terrain ? 1_300_000.0 * config.terrainQuality / 100.0 * (dragButton >= 0 ? 0.45 : 1.0)
                : 2_400_000.0;
        double ratio = Math.min(1.0, Math.sqrt(budget / Math.max(1.0, pixelsWide * pixelsHigh)));
        int renderWidth = Math.max(16, (int) Math.round(pixelsWide * ratio));
        int renderHeight = Math.max(16, (int) Math.round(pixelsHigh * ratio));
        double[] state = {centerX, centerZ, scale, terrain ? 1 : 0, yaw, pitch, ground, renderWidth, renderHeight,
            grid ? 1 : 0, System.identityHashCode(store), config.terrainFov, config.terrainVertical,
            config.terrainFog ? 1 : 0};
        boolean changed = !Arrays.equals(state, lastState);
        if (!changed && clock() - lastRequest < 0.25) {
            return;
        }
        lastState = state;
        lastRequest = clock();
        Params params;
        if (terrain) {
            double reach = Math.max(1200.0, distance() * 7.0);
            params = new Params(store, null, new TerrainRenderer.Scene(camera(), config.terrainVertical, reach,
                    config.terrainFog, 0x141A26, 0x4A5870, 400));
        } else {
            params = new Params(store, new MapPainter.View(centerX, centerZ, scale * ratio, 0.0, BACKGROUND, grid),
                    null);
        }
        frames.request(params, renderWidth, renderHeight);
    }

    private void markers(Canvas canvas, Minecraft minecraft, int width, int height) {
        if (shown == null) {
            return;
        }
        WaypointStore store = Atlas.waypoints();
        Waypoint hovered = overMap() ? waypointAt(mouseX, mouseY) : null;
        for (Waypoint waypoint : store.in(dimension)) {
            if (!store.shown(waypoint) || !place(waypoint.x + 0.5, waypoint.y + 1.0, waypoint.z + 0.5, width,
                    height)) {
                continue;
            }
            float x = (float) point[0];
            float y = (float) point[1];
            if (x < -10 || y < -10 || x > width + 10 || y > height + 10) {
                continue;
            }
            boolean hot = hovered != null && hovered.id.equals(waypoint.id);
            HudOverlay.icon(canvas, waypoint, x, y, hot ? 1.5f : 1.2f, hidden(x, y, width, height) ? 0.4f : 1f);
            if (hot || scale >= guiScale) {
                String name = waypoint.name;
                int w = canvas.textWidth(name);
                canvas.fill(Math.round(x) - w / 2 - 2, Math.round(y) + 9, w + 4, canvas.lineHeight() + 1, 0x90000000);
                canvas.text(name, Math.round(x) - w / 2, Math.round(y) + 10, 0xFFFFFFFF, false);
            }
        }
        LocalPlayer player = minecraft.player;
        if (player != null && dimension.equals(Atlas.dimension())) {
            float partial = Atlas.partial();
            if (place(World.x(player, partial), World.y(player, partial) + 1.0, World.z(player, partial), width,
                    height)) {
                arrow(canvas, (float) point[0], (float) point[1], terrain ? (float) (World.yaw(player, partial) - yaw)
                        : World.yaw(player, partial) + 180f);
            }
        }
    }

    private boolean place(double x, double y, double z, int width, int height) {
        if (shown.scene() != null) {
            return shown.scene().camera().project(x, y * shown.scene().vertical(), z, width, height, point);
        }
        MapPainter.View view = shown.view();
        double perGui = view.scale() / shownRatio;
        point[0] = width / 2.0 + (x - view.centerX()) * perGui;
        point[1] = height / 2.0 + (z - view.centerZ()) * perGui;
        point[2] = 0.0;
        return true;
    }

    private boolean hidden(float x, float y, int width, int height) {
        if (shown.scene() == null || shownDepth == null) {
            return false;
        }
        int px = (int) (x / width * depthWidth);
        int py = (int) (y / height * depthHeight);
        if (px < 0 || py < 0 || px >= depthWidth || py >= depthHeight) {
            return false;
        }
        Camera camera = shown.scene().camera();
        double forward = point[2];
        double u = (x / width * 2.0 - 1.0) * camera.tanHalf * camera.aspect;
        double v = (1.0 - y / height * 2.0) * camera.tanHalf;
        double along = forward * Math.sqrt(1.0 + u * u + v * v);
        return shownDepth[py * depthWidth + px] < along - 3.0;
    }

    private static void arrow(Canvas canvas, float x, float y, float degrees) {
        canvas.push();
        canvas.translate(x, y);
        canvas.rotate(degrees);
        Pixels.draw(canvas, 0, 0, 0xFF10131A, "....#....", "...###...", "...###...", "..#####..", "..#####..",
                ".#######.", ".#######.", "####.####", "###...###");
        Pixels.draw(canvas, 0, 0, 0xFFFFFFFF, ".........", "....#....", "....#....", "...###...", "...###...",
                "..#####..", "..#####..", ".###.###.", ".........");
        canvas.pop();
    }

    private void toolbar(Theme theme, Canvas canvas, int width) {
        int x = 6;
        int y = 6;
        int total = Math.min(width - 12 - (list ? LIST + 6 : 0), 470);
        canvas.fill(x, y, total, BAR + 8, 0xB8101318);
        theme.card(canvas, x, y, total, BAR + 8);
        blocked.add(new int[] {x, y, total, BAR + 8});
        x += 4;
        y += 4;
        int picked = gui().segmented("mode", x, y, 60, BAR, new String[] {"2D", "3D"}, terrain ? 1 : 0,
                new String[] {Atlas.text("atlas.map.flat.tooltip"), Atlas.text("atlas.map.terrain.tooltip")}, null);
        if (picked >= 0 && (picked == 1) != terrain) {
            toggleTerrain();
        }
        x += 64;
        if (gui().button("dimension", x, y, 104, BAR, gui().fit(HudOverlay.pretty(dimension), 96),
                Atlas.text("atlas.map.dimension.tooltip"))) {
            nextDimension();
        }
        x += 108;
        boolean allowed = Atlas.cavesAllowed();
        if (gui().button("cave", x, y, 58, BAR, Atlas.text(cave && allowed ? "atlas.map.cave" : "atlas.map.surface"),
                ButtonKind.NORMAL, cave && allowed, allowed, Atlas.text(allowed ? "atlas.map.cave.tooltip"
                        : "atlas.map.cave.blocked"))) {
            cave = !cave;
        }
        x += 62;
        if (cave && allowed) {
            float value = (layer + 64) / 384f;
            float moved = gui().valueSlider("layer", x, y, 96, BAR, "Y " + layer, value, value, true,
                    Atlas.text("atlas.map.layer.tooltip"));
            int chosen = MapWorld.layer(Math.round(moved * 384f) - 64);
            if (chosen != layer) {
                layer = chosen;
            }
            x += 100;
        }
        if (gui().button("grid", x, y, 40, BAR, Atlas.text("atlas.map.grid"), ButtonKind.NORMAL, grid, !terrain,
                Atlas.text("atlas.map.grid.tooltip"))) {
            grid = !grid;
        }
        x += 44;
        if (gui().button("me", x, y, 40, BAR, Atlas.text("atlas.map.me"), Atlas.text("atlas.map.me.tooltip"))) {
            home();
        }
        x += 44;
        if (gui().button("list", x, y, 40, BAR, Atlas.text("atlas.map.list"), ButtonKind.NORMAL, list, true,
                Atlas.text("atlas.map.list.tooltip"))) {
            list = !list;
        }
        x += 44;
        if (x + 54 <= 6 + total && gui().button("settings", x, y, 54, BAR, Atlas.text("atlas.map.settings"),
                Atlas.text("atlas.map.settings.tooltip"))) {
            Minecraft minecraft = Minecraft.getInstance();
            Compat.show(minecraft, AtlasMenu.create(null));
        }
    }

    private void home() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        dimension = Atlas.dimension();
        centerX = player.getX();
        centerZ = player.getZ();
        ground = player.getY();
        targetScale = scale;
        cave = Atlas.caveActive();
        layer = MapWorld.layer(player.blockPosition().getY());
    }

    private void nextDimension() {
        MapWorld world = Atlas.world();
        if (world == null) {
            return;
        }
        List<String> known = world.dimensions();
        if (!known.contains(Atlas.dimension())) {
            known.add(Atlas.dimension());
        }
        known.sort(Comparator.naturalOrder());
        int index = known.indexOf(dimension);
        dimension = known.get((index + 1) % known.size());
        if (dimension.equals(Atlas.dimension())) {
            cave = Atlas.caveActive();
        } else {
            cave = dimension.endsWith("the_nether");
        }
        lastState = new double[0];
    }

    private void status(Canvas canvas, MapStore store, int width, int height) {
        String text;
        if (terrain) {
            text = (int) Math.floor(centerX) + ", " + (int) Math.floor(centerZ) + "   "
                    + Atlas.text("atlas.map.terrain.hint");
        } else {
            int x = (int) Math.floor(worldX(mouseX));
            int z = (int) Math.floor(worldZ(mouseY));
            int top = store == null ? Region.NONE : store.height(x, z);
            text = x + ", " + z + (top == Region.NONE ? "" : "   Y " + top) + "   "
                    + Atlas.text("atlas.map.flat.hint");
        }
        int w = canvas.textWidth(text);
        canvas.fill(6, height - 18, w + 8, 13, 0xA0101318);
        canvas.text(text, 10, height - 15, 0xFFE8ECF2, false);
        if (Atlas.config().honorServerRules && Atlas.fair().limited()) {
            String note = Atlas.text("atlas.map.limited");
            int nw = canvas.textWidth(note);
            canvas.fill(6, height - 34, nw + 8, 13, 0xA0101318);
            canvas.text(note, 10, height - 31, 0xFFFFC857, false);
        }
    }

    private void panel(Theme theme, Canvas canvas, Minecraft minecraft, int width, int height) {
        int x = width - LIST - 6;
        int y = 6;
        int h = height - 12;
        canvas.fill(x, y, LIST, h, 0xB8101318);
        theme.card(canvas, x, y, LIST, h);
        blocked.add(new int[] {x, y, LIST, h});
        int ix = x + 6;
        int iw = LIST - 12;
        String query = gui().field("search", ix, y + 6, iw, 18, Atlas.text("atlas.map.search"), 40);
        WaypointStore store = Atlas.waypoints();
        List<Waypoint> found = store.search(query);
        LocalPlayer player = minecraft.player;
        double px = player == null ? 0 : player.getX();
        double py = player == null ? 0 : player.getY();
        double pz = player == null ? 0 : player.getZ();
        String here = Atlas.dimension();
        found.sort(Comparator.comparing((Waypoint waypoint) -> waypoint.group)
                .thenComparing(waypoint -> !waypoint.dimension.equals(here))
                .thenComparingDouble(waypoint -> waypoint.distance(px, py, pz)));
        int top = y + 30;
        int footer = y + h - 26;
        int viewHeight = footer - 4 - top;
        float scroll = gui().scrollBegin("waypoints", ix, top, iw, viewHeight);
        int rowY = top - Math.round(scroll);
        int start = rowY;
        String group = null;
        int rowWidth = iw - 8;
        for (Waypoint waypoint : found) {
            if (!waypoint.group.equals(group)) {
                group = waypoint.group;
                boolean hiddenGroup = store.groupHidden(group);
                String title = group.isEmpty() ? Atlas.text("atlas.map.ungrouped") : group;
                if (gui().button("group-" + group, ix, rowY, rowWidth, 14, gui().fit(title, rowWidth - 40) + "  "
                        + Atlas.text(hiddenGroup ? "atlas.map.hidden" : "atlas.map.shown"), ButtonKind.GHOST, false,
                        true, Atlas.text("atlas.map.group.tooltip"))) {
                    store.hideGroup(group, !hiddenGroup);
                }
                rowY += 16;
            }
            String id = "row-" + waypoint.id;
            gui().rowBackground(id, ix, rowY, rowWidth, 22, false);
            boolean visible = store.shown(waypoint);
            HudOverlay.icon(canvas, waypoint, ix + 10, rowY + 11, 1f, visible ? 1f : 0.4f);
            canvas.text(gui().fit(waypoint.name, rowWidth - 62), ix + 22, rowY + 3,
                    visible ? theme.textPrimary() : theme.textSecondary(), false);
            String detail = waypoint.dimension.equals(here) ? HudOverlay.distance(waypoint.distance(px, py, pz))
                    : HudOverlay.pretty(waypoint.dimension);
            canvas.text(gui().fit(detail, rowWidth - 62), ix + 22, rowY + 12, theme.textSecondary(), false);
            if (gui().button(id + "-eye", ix + rowWidth - 38, rowY + 3, 16, 16, waypoint.visible ? "o" : "-",
                    ButtonKind.GHOST, false, true, Atlas.text("atlas.map.toggle.tooltip"))) {
                waypoint.visible = !waypoint.visible;
                store.update(waypoint);
            } else if (gui().button(id + "-edit", ix + rowWidth - 20, rowY + 3, 16, 16, "...", ButtonKind.GHOST,
                    false, true, Atlas.text("atlas.map.edit.tooltip"))) {
                edit(waypoint, false);
            } else if (gui().hit(id, ix, rowY, rowWidth - 40, 22, true, Atlas.text("atlas.map.jump.tooltip"))) {
                dimension = waypoint.dimension;
                centerX = waypoint.x + 0.5;
                centerZ = waypoint.z + 0.5;
                ground = waypoint.y;
                targetScale = scale;
            }
            rowY += 24;
        }
        if (found.isEmpty()) {
            canvas.text(gui().fit(Atlas.text("atlas.map.empty"), rowWidth), ix + 2, rowY + 4, theme.textSecondary(),
                    false);
            rowY += 16;
        }
        gui().scrollEnd(rowY - start);
        int third = (iw - 8) / 3;
        if (gui().button("new", ix, footer, third, 20, Atlas.text("atlas.map.new"), ButtonKind.PRIMARY, false, true,
                Atlas.text("atlas.map.new.tooltip"))) {
            create((int) Math.floor(centerX), (int) Math.floor(centerZ));
        }
        if (gui().button("import", ix + third + 4, footer, third, 20, Atlas.text("atlas.map.import"),
                Atlas.text("atlas.map.import.tooltip"))) {
            sources = Importers.find(minecraft.gameDirectory.toPath());
        }
        if (gui().button("export", ix + 2 * third + 8, footer, third, 20, Atlas.text("atlas.map.export"),
                Atlas.text("atlas.map.export.tooltip"))) {
            export(minecraft);
        }
    }

    private void export(Minecraft minecraft) {
        MapWorld world = Atlas.world();
        if (world == null) {
            return;
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = minecraft.gameDirectory.toPath().resolve("atlas").resolve("export")
                .resolve(world.root().getFileName() + "-" + stamp + ".json");
        WaypointStore.Document document = Atlas.waypoints().document();
        Atlas.shared().io.execute(() -> {
            boolean saved = WaypointStore.write(file, document);
            ToastOverlay.show(Atlas.text(saved ? "atlas.toast.exported" : "atlas.toast.failed"),
                    "atlas/export/" + file.getFileName(), "filled_map");
        });
    }

    private void contextMenu(Theme theme, Canvas canvas, Minecraft minecraft) {
        int w = 130;
        List<String> actions = new ArrayList<>();
        if (menuTarget != null) {
            actions.add("edit");
            actions.add(menuTarget.visible ? "hide" : "show");
            actions.add("delete");
        } else {
            actions.add("create");
        }
        LocalPlayer player = minecraft.player;
        boolean teleport = Atlas.config().teleport && player != null && Compat.permitted(player)
                && dimension.equals(Atlas.dimension());
        if (teleport) {
            actions.add("teleport");
        }
        int h = actions.size() * 20 + 6;
        canvas.fill(menuX, menuY, w, h, 0xD8101318);
        theme.card(canvas, menuX, menuY, w, h);
        blocked.add(new int[] {menuX, menuY, w, h});
        int y = menuY + 3;
        for (String action : actions) {
            if (gui().button("menu-" + action, menuX + 3, y, w - 6, 18, Atlas.text("atlas.menu." + action),
                    action.equals("delete") ? ButtonKind.DANGER : ButtonKind.GHOST, false, true, null)) {
                run(action, player);
            }
            y += 20;
        }
    }

    private void run(String action, LocalPlayer player) {
        menu = false;
        WaypointStore store = Atlas.waypoints();
        switch (action) {
            case "create" -> create(menuWorldX, menuWorldZ);
            case "edit" -> edit(menuTarget, false);
            case "hide", "show" -> {
                menuTarget.visible = !menuTarget.visible;
                store.update(menuTarget);
            }
            case "delete" -> store.remove(menuTarget.id);
            case "teleport" -> {
                if (menuTarget != null) {
                    teleport(player, menuTarget.x, menuTarget.y, menuTarget.z);
                } else {
                    teleport(player, menuWorldX, surface(menuWorldX, menuWorldZ), menuWorldZ);
                }
            }
            default -> {
            }
        }
    }

    private void teleport(LocalPlayer player, int x, int y, int z) {
        if (player == null || !Atlas.config().teleport || !Compat.permitted(player)) {
            return;
        }
        World.command(player, Atlas.config().teleportCommand.replace("{x}", Integer.toString(x))
                .replace("{y}", Integer.toString(y)).replace("{z}", Integer.toString(z)));
        requestClose();
    }

    private void editor(Theme theme, Canvas canvas, Minecraft minecraft, int width, int height) {
        int w = Math.min(270, width - 20);
        int h = Math.min(262, height - 12);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        theme.window(canvas, x, y, w, h);
        int ix = x + 10;
        int iw = w - 20;
        int row = y + 10;
        gui().text(Atlas.text(editingNew ? "atlas.editor.new" : "atlas.editor.edit"), ix, row, theme.titleColor());
        gui().right(HudOverlay.pretty(editing.dimension), ix + iw, row, theme.textSecondary());
        row += 16;
        editing.name = gui().field("wp-name", ix, row, iw, 18, Atlas.text("atlas.editor.name"), 48);
        row += 22;
        int third = (iw - 8) / 3;
        editing.x = number(gui().field("wp-x", ix, row, third, 18, "X", 9), editing.x);
        editing.y = number(gui().field("wp-y", ix + third + 4, row, third, 18, "Y", 6), editing.y);
        editing.z = number(gui().field("wp-z", ix + 2 * third + 8, row, third, 18, "Z", 9), editing.z);
        row += 22;
        editing.group = gui().field("wp-group", ix, row, iw, 18, Atlas.text("atlas.editor.group"), 32);
        row += 24;
        for (int i = 0; i < Waypoint.COLORS.length; i++) {
            int color = Waypoint.COLORS[i];
            if (gui().swatch("wp-color-" + i, ix + 2 + i * (iw - 4) / Waypoint.COLORS.length, row, 14,
                    0xFF000000 | color, editing.color == color, false, null)) {
                editing.color = color;
            }
        }
        row += 22;
        List<String> icons = Icons.names();
        int perRow = 8;
        int cell = Math.min(24, iw / perRow);
        for (int i = 0; i < icons.size(); i++) {
            String name = icons.get(i);
            int cx = ix + (i % perRow) * cell;
            int cy = row + (i / perRow) * cell;
            if (gui().pad("wp-icon-" + name, cx, cy, cell - 2, cell - 2, name.equals(editing.icon), null)) {
                editing.icon = name;
            }
            Pixels.draw(canvas, cx + (cell - 2) / 2, cy + (cell - 2) / 2, 0xFF000000 | editing.color,
                    Icons.rows(name));
        }
        row += ((icons.size() + perRow - 1) / perRow) * cell + 4;
        String on = Atlas.text("atlas.state.on");
        String off = Atlas.text("atlas.state.off");
        if (gui().switchRow("wp-visible", ix, row, iw, 18, Atlas.text("atlas.editor.visible"), editing.visible, true,
                null, on, off)) {
            editing.visible = !editing.visible;
        }
        row += 20;
        if (gui().switchRow("wp-beam", ix, row, iw, 18, Atlas.text("atlas.editor.beam"), editing.beam, true, null,
                on, off)) {
            editing.beam = !editing.beam;
        }
        row += 20;
        boolean temporary = editing.kind == Waypoint.Kind.TEMPORARY;
        if (editing.kind != Waypoint.Kind.DEATH && gui().switchRow("wp-temporary", ix, row, iw, 18,
                Atlas.text("atlas.editor.temporary"), temporary, true, Atlas.text("atlas.editor.temporary.tooltip"),
                on, off)) {
            editing.kind = temporary ? Waypoint.Kind.NORMAL : Waypoint.Kind.TEMPORARY;
        }
        int footer = y + h - 30;
        int button = (iw - 8) / 3;
        WaypointStore store = Atlas.waypoints();
        if (gui().button("wp-save", ix, footer, button, 20, Atlas.text("atlas.editor.save"), ButtonKind.PRIMARY,
                false, true, null) || gui().submitted("wp-name")) {
            store.update(editing);
            editing = null;
            return;
        }
        if (gui().button("wp-cancel", ix + button + 4, footer, button, 20, Atlas.text("atlas.editor.cancel"), null)) {
            editing = null;
            return;
        }
        if (!editingNew && gui().button("wp-delete", ix + 2 * button + 8, footer, button, 20,
                Atlas.text("atlas.menu.delete"), ButtonKind.DANGER, false, true, null)) {
            store.remove(editing.id);
            editing = null;
        }
    }

    private static int number(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private void importer(Theme theme, Canvas canvas, int width, int height) {
        int w = Math.min(300, width - 20);
        int h = Math.min(240, height - 12);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        theme.window(canvas, x, y, w, h);
        int ix = x + 10;
        int iw = w - 20;
        gui().text(Atlas.text("atlas.import.title"), ix, y + 10, theme.titleColor());
        int top = y + 28;
        int footer = y + h - 30;
        float scroll = gui().scrollBegin("sources", ix, top, iw, footer - 6 - top);
        int row = top - Math.round(scroll);
        int start = row;
        if (sources.isEmpty()) {
            for (String line : Draw.wrap(canvas,
                    Atlas.text("atlas.import.none"), iw - 12)) {
                canvas.text(line, ix, row, theme.textSecondary(), false);
                row += canvas.lineHeight() + 2;
            }
        }
        for (int i = 0; i < sources.size(); i++) {
            Importers.Source source = sources.get(i);
            String label = Atlas.text("atlas.import." + source.kind()) + "  " + source.label();
            if (gui().button("source-" + i, ix, row, iw - 10, 20, gui().fit(label, iw - 20),
                    Atlas.text("atlas.import.tooltip"))) {
                WaypointStore store = Atlas.waypoints();
                Atlas.shared().io.execute(() -> {
                    int added = store.merge(Importers.load(source));
                    ToastOverlay.show(Atlas.text("atlas.toast.imported"), Atlas.text("atlas.toast.imported.count",
                            added), "filled_map");
                });
                sources = null;
                return;
            }
            row += 22;
        }
        gui().scrollEnd(row - start);
        if (gui().button("import-close", ix, footer, iw, 20, Atlas.text("atlas.editor.cancel"), null)) {
            sources = null;
        }
    }
}
