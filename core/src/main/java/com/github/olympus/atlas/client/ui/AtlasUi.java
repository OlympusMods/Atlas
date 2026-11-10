package com.github.olympus.atlas.client.ui;

import com.github.olympus.atlas.client.Atlas;
import com.github.olympus.atlas.core.AtlasConfig;
import com.github.olympus.proteus.client.ui.ButtonKind;
import com.github.olympus.proteus.client.ui.Theme;
import com.github.olympus.proteus.client.ui.UiHost;
import com.github.olympus.proteus.client.ui.UiShell;
import java.util.Locale;

public final class AtlasUi extends UiShell {
    private static final int PAD = 10;
    private static final int GAP = 6;
    private static final int ROW = 20;
    private static final int LINE = 24;

    private enum Tab {
        MINIMAP,
        MAP,
        TERRAIN,
        WAYPOINTS,
        DATA
    }

    private final AtlasConfig config = Atlas.config();
    private Tab tab = Tab.MINIMAP;
    private boolean dirty;
    private int rowX;
    private int rowW;
    private int stepperW;

    public AtlasUi(UiHost host) {
        super(host);
    }

    @Override
    public void removed() {
        flush();
    }

    @Override
    protected void onTick() {
        flush();
    }

    private void flush() {
        if (dirty) {
            dirty = false;
            Atlas.save();
        }
    }

    @Override
    protected void renderContent(Theme theme) {
        int pw = windowWidth();
        int ph = windowHeight();
        int px = windowX(pw);
        int py = windowY(ph);
        theme.window(fade(), px, py, pw, ph);
        int ix = px + PAD;
        int iw = pw - 2 * PAD;
        int y = py + PAD;
        String title = text("atlas.screen.title");
        if (theme.centeredTitle() && pw >= 340) {
            gui().centered(title, px + pw / 2, y + 5, theme.titleColor());
        } else {
            fade().push();
            fade().translate(ix, y + 2);
            fade().scale(1.25f, 1.25f);
            gui().text(title, 0, 0, theme.titleColor());
            fade().pop();
        }
        y += 26;
        Tab[] all = Tab.values();
        String[] tabs = new String[all.length];
        String[] tips = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            String key = "atlas.tab." + all[i].name().toLowerCase(Locale.ROOT);
            tabs[i] = text(key);
            tips[i] = text(key + ".tooltip");
        }
        int chosen = gui().segmented("tabs", ix, y, iw, 20, tabs, tab.ordinal(), tips, null);
        if (chosen >= 0) {
            tab = all[chosen];
            restartContentFade();
        }
        y += 20 + GAP;
        int footerY = py + ph - PAD - ROW;
        int hintY = footerY - GAP - 10;
        int viewH = hintY - GAP - y;
        float before = beginContent();
        float scroll = gui().scrollBegin("options-" + tab.name(), ix, y, iw, viewH);
        rowX = ix + 6;
        rowW = iw - 22;
        stepperW = Math.min(124, rowW / 2);
        int top = y - Math.round(scroll);
        int end = switch (tab) {
            case MINIMAP -> minimapRows(top);
            case MAP -> mapRows(top);
            case TERRAIN -> terrainRows(top);
            case WAYPOINTS -> waypointRows(top);
            case DATA -> dataRows(top);
        };
        gui().scrollEnd(end - top);
        endContent(before);
        gui().text(gui().fit(text("atlas.screen.keys"), iw), ix, hintY, theme.textSecondary());
        if (gui().button("done", ix, footerY, iw, ROW, text("atlas.screen.done"), ButtonKind.PRIMARY, false, true,
                null)) {
            flush();
            requestClose();
        }
    }

    private int minimapRows(int y) {
        config.minimapEnabled = toggle("minimapEnabled", y, config.minimapEnabled);
        y += LINE;
        config.minimapShape = choice("minimapShape", y, config.minimapShape, AtlasConfig.Shape.values());
        y += LINE;
        config.minimapRotate = toggle("minimapRotate", y, config.minimapRotate);
        y += LINE;
        config.minimapTilt = toggle("minimapTilt", y, config.minimapTilt);
        y += LINE;
        config.minimapZoom = (int) number("minimapZoom", y, config.minimapZoom, 1, 0,
                AtlasConfig.MINIMAP_ZOOMS.length - 1, zoom(config.minimapScale()));
        y += LINE;
        config.minimapCorner = choice("minimapCorner", y, config.minimapCorner, AtlasConfig.Corner.values());
        y += LINE;
        config.minimapOffsetX = (int) number("minimapOffsetX", y, config.minimapOffsetX, 2, 0, 400,
                config.minimapOffsetX + " px");
        y += LINE;
        config.minimapOffsetY = (int) number("minimapOffsetY", y, config.minimapOffsetY, 2, 0, 400,
                config.minimapOffsetY + " px");
        y += LINE;
        config.minimapSize = (int) number("minimapSize", y, config.minimapSize, 10, 60, 260,
                config.minimapSize + " px");
        y += LINE;
        config.minimapOpacity = (int) number("minimapOpacity", y, config.minimapOpacity, 5, 20, 100,
                config.minimapOpacity + "%");
        y += LINE;
        config.minimapCoords = toggle("minimapCoords", y, config.minimapCoords);
        y += LINE;
        config.minimapBiome = toggle("minimapBiome", y, config.minimapBiome);
        y += LINE;
        config.minimapLight = toggle("minimapLight", y, config.minimapLight);
        y += LINE;
        config.minimapCompass = toggle("minimapCompass", y, config.minimapCompass);
        y += LINE;
        config.minimapWaypoints = toggle("minimapWaypoints", y, config.minimapWaypoints);
        y += LINE;
        config.minimapHideInScreens = toggle("minimapHideInScreens", y, config.minimapHideInScreens);
        y += LINE;
        config.radarPlayers = toggle("radarPlayers", y, config.radarPlayers);
        y += LINE;
        config.radarHostile = toggle("radarHostile", y, config.radarHostile);
        y += LINE;
        config.radarPassive = toggle("radarPassive", y, config.radarPassive);
        y += LINE;
        config.radarHideSneaking = toggle("radarHideSneaking", y, config.radarHideSneaking);
        y += LINE;
        config.radarHideInvisible = toggle("radarHideInvisible", y, config.radarHideInvisible);
        y += LINE;
        config.radarIdentify = toggle("radarIdentify", y, config.radarIdentify);
        y += LINE;
        return y;
    }

    private int mapRows(int y) {
        config.colorMode = choice("colorMode", y, config.colorMode, AtlasConfig.ColorMode.values());
        y += LINE;
        config.heightShading = toggle("heightShading", y, config.heightShading);
        y += LINE;
        if (config.heightShading) {
            config.shadingStrength = (int) number("shadingStrength", y, config.shadingStrength, 10, 0, 100,
                    config.shadingStrength + "%");
            y += LINE;
        }
        config.waterDepth = toggle("waterDepth", y, config.waterDepth);
        y += LINE;
        config.biomeTint = toggle("biomeTint", y, config.biomeTint);
        y += LINE;
        config.caveMode = choice("caveMode", y, config.caveMode, AtlasConfig.CaveMode.values());
        y += LINE;
        config.mapGrid = toggle("mapGrid", y, config.mapGrid);
        y += LINE;
        config.mapSmoothZoom = toggle("mapSmoothZoom", y, config.mapSmoothZoom);
        y += LINE;
        config.mapWaypointList = toggle("mapWaypointList", y, config.mapWaypointList);
        y += LINE;
        config.teleport = toggle("teleport", y, config.teleport);
        y += LINE;
        if (config.teleport) {
            label("teleportCommand", y);
            int width = Math.min(220, rowW * 3 / 5);
            if (!gui().typing() && !primed) {
                gui().setField("teleportCommand", config.teleportCommand);
                primed = true;
            }
            String typed = gui().field("teleportCommand", rowX + rowW - width, y, width, ROW, "tp @s {x} {y} {z}",
                    80);
            if (!typed.isBlank() && !typed.equals(config.teleportCommand)) {
                config.teleportCommand = typed;
                dirty = true;
            }
            y += LINE;
        }
        config.honorServerRules = toggle("honorServerRules", y, config.honorServerRules);
        y += LINE;
        return y;
    }

    private boolean primed;

    private int terrainRows(int y) {
        config.terrainQuality = (int) number("terrainQuality", y, config.terrainQuality, 5, 25, 100,
                config.terrainQuality + "%");
        y += LINE;
        config.terrainFov = (int) number("terrainFov", y, config.terrainFov, 5, 30, 90, config.terrainFov + "°");
        y += LINE;
        config.terrainVertical = number("terrainVertical", y, config.terrainVertical, 0.25, 0.5, 4.0,
                String.format(Locale.ROOT, "x%.2f", config.terrainVertical));
        y += LINE;
        config.terrainFog = toggle("terrainFog", y, config.terrainFog);
        y += LINE;
        return y;
    }

    private int waypointRows(int y) {
        config.waypointsInWorld = toggle("waypointsInWorld", y, config.waypointsInWorld);
        y += LINE;
        config.waypointNames = toggle("waypointNames", y, config.waypointNames);
        y += LINE;
        config.waypointDistance = toggle("waypointDistance", y, config.waypointDistance);
        y += LINE;
        config.waypointBeams = toggle("waypointBeams", y, config.waypointBeams);
        y += LINE;
        config.waypointMaxDistance = (int) number("waypointMaxDistance", y, config.waypointMaxDistance, 250, 0,
                100000, config.waypointMaxDistance == 0 ? text("atlas.value.unlimited")
                        : config.waypointMaxDistance + " m");
        y += LINE;
        config.waypointScale = (int) number("waypointScale", y, config.waypointScale, 10, 50, 300,
                config.waypointScale + "%");
        y += LINE;
        config.waypointOcclusion = choice("waypointOcclusion", y, config.waypointOcclusion,
                AtlasConfig.Occlusion.values());
        y += LINE;
        config.deathWaypoints = toggle("deathWaypoints", y, config.deathWaypoints);
        y += LINE;
        if (config.deathWaypoints) {
            config.deathLimit = (int) number("deathLimit", y, config.deathLimit, 1, 1, 50,
                    Integer.toString(config.deathLimit));
            y += LINE;
            config.deathExpiryHours = (int) number("deathExpiryHours", y, config.deathExpiryHours, 1, 0, 720,
                    config.deathExpiryHours == 0 ? text("atlas.value.never") : config.deathExpiryHours + " h");
            y += LINE;
        }
        config.temporaryRemoveOnArrival = toggle("temporaryRemoveOnArrival", y, config.temporaryRemoveOnArrival);
        y += LINE;
        config.temporaryExpiryMinutes = (int) number("temporaryExpiryMinutes", y, config.temporaryExpiryMinutes, 5,
                0, 1440, config.temporaryExpiryMinutes == 0 ? text("atlas.value.never")
                        : config.temporaryExpiryMinutes + " min");
        y += LINE;
        return y;
    }

    private int dataRows(int y) {
        config.memoryLimitMb = (int) number("memoryLimitMb", y, config.memoryLimitMb, 64, 64, 2048,
                config.memoryLimitMb + " MB");
        y += LINE;
        config.workerThreads = (int) number("workerThreads", y, config.workerThreads, 1, 0, 8,
                config.workerThreads == 0 ? text("atlas.value.auto") : Integer.toString(config.workerThreads));
        y += LINE;
        config.scanRadius = (int) number("scanRadius", y, config.scanRadius, 1, 0, 32,
                config.scanRadius == 0 ? text("atlas.value.auto") : config.scanRadius + " ch");
        y += LINE;
        config.saveIntervalSeconds = (int) number("saveIntervalSeconds", y, config.saveIntervalSeconds, 5, 5, 600,
                config.saveIntervalSeconds + " s");
        y += LINE;
        long used = Atlas.shared().used.get() >> 20;
        gui().text(gui().fit(text("atlas.screen.memory", used, config.memoryLimitMb), rowW), rowX,
                gui().textY(y, ROW), gui().theme().textSecondary());
        y += LINE;
        return y;
    }

    private boolean toggle(String id, int y, boolean value) {
        if (gui().switchRow(id, rowX, y, rowW, ROW, text("atlas.option." + id), value, true,
                text("atlas.option." + id + ".tooltip"), text("atlas.state.on"), text("atlas.state.off"))) {
            dirty = true;
            return !value;
        }
        return value;
    }

    private void label(String id, int y) {
        gui().text(gui().fit(text("atlas.option." + id), rowW - stepperW - 8), rowX, gui().textY(y, ROW),
                gui().theme().textPrimary());
    }

    private <E extends Enum<E>> E choice(String id, int y, E value, E[] values) {
        label(id, y);
        String[] labels = new String[values.length];
        String[] tips = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = text("atlas.choice." + id + "." + values[i].name().toLowerCase(Locale.ROOT));
            tips[i] = text("atlas.option." + id + ".tooltip");
        }
        int width = Math.min(values.length > 3 ? 250 : 210, rowW * 3 / 5);
        int picked = gui().segmented(id, rowX + rowW - width, y, width, ROW, labels, value.ordinal(), tips, null);
        if (picked >= 0 && picked != value.ordinal()) {
            dirty = true;
            return values[picked];
        }
        return value;
    }

    private double number(String id, int y, double value, double step, double low, double high, String shown) {
        label(id, y);
        int change = gui().stepper(id, rowX + rowW - stepperW, y, stepperW, ROW, shown,
                text("atlas.option." + id + ".tooltip"), value > low + 1.0e-9, value < high - 1.0e-9);
        if (change == 0) {
            return value;
        }
        dirty = true;
        double next = Math.round((value + change * step) / step) * step;
        return Math.max(low, Math.min(high, Math.round(next * 1000.0) / 1000.0));
    }

    private static String zoom(double scale) {
        return scale >= 1.0 ? String.format(Locale.ROOT, "%.0fx", scale) : String.format(Locale.ROOT, "1/%.0f", 1.0 / scale);
    }

    private static String text(String key, Object... arguments) {
        return Atlas.text(key, arguments);
    }
}
