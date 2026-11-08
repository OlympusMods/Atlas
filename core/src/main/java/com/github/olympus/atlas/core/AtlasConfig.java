package com.github.olympus.atlas.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtlasConfig {
    public static final double[] MINIMAP_ZOOMS = {0.25, 0.5, 1.0, 2.0, 4.0, 8.0};

    private static final Logger LOGGER = LoggerFactory.getLogger("atlas");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public enum Shape {
        ROUND,
        SQUARE
    }

    public enum Corner {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public enum ColorMode {
        TEXTURE,
        MAP
    }

    public enum CaveMode {
        OFF,
        AUTO,
        ON
    }

    public enum Occlusion {
        ALWAYS,
        DIM,
        HIDE
    }

    public int version = 2;

    public boolean minimapEnabled = true;
    public Shape minimapShape = Shape.ROUND;
    public boolean minimapRotate = true;
    public int minimapZoom = 2;
    public Corner minimapCorner = Corner.TOP_RIGHT;
    public int minimapOffsetX = 6;
    public int minimapOffsetY = 6;
    public int minimapSize = 110;
    public int minimapOpacity = 100;
    public boolean minimapCoords = true;
    public boolean minimapBiome = true;
    public boolean minimapLight = false;
    public boolean minimapCompass = true;
    public boolean minimapTilt = false;
    public boolean minimapWaypoints = true;
    public boolean minimapHideInScreens = true;

    public boolean radarPlayers = true;
    public boolean radarHostile = true;
    public boolean radarPassive = false;
    public boolean radarHideSneaking = true;
    public boolean radarHideInvisible = true;

    public ColorMode colorMode = ColorMode.TEXTURE;
    public boolean heightShading = true;
    public int shadingStrength = 60;
    public boolean waterDepth = true;
    public boolean biomeTint = true;
    public CaveMode caveMode = CaveMode.AUTO;
    public boolean mapGrid = false;
    public boolean mapSmoothZoom = true;
    public boolean mapWaypointList = true;
    public boolean teleport = false;
    public String teleportCommand = "tp @s {x} {y} {z}";

    public int terrainQuality = 50;
    public boolean terrainFog = true;
    public double terrainVertical = 1.0;
    public int terrainFov = 55;

    public boolean waypointsInWorld = true;
    public boolean waypointNames = false;
    public boolean waypointDistance = false;
    public boolean waypointBeams = true;
    public int waypointMaxDistance = 0;
    public int waypointScale = 100;
    public Occlusion waypointOcclusion = Occlusion.ALWAYS;
    public boolean deathWaypoints = true;
    public int deathLimit = 3;
    public int deathExpiryHours = 0;
    public boolean temporaryRemoveOnArrival = true;
    public int temporaryExpiryMinutes = 30;

    public int memoryLimitMb = 256;
    public int workerThreads = 0;
    public int scanRadius = 0;
    public int saveIntervalSeconds = 30;

    public boolean honorServerRules = true;

    public AtlasConfig sanitize() {
        if (minimapShape == null) {
            minimapShape = Shape.ROUND;
        }
        if (minimapCorner == null) {
            minimapCorner = Corner.TOP_RIGHT;
        }
        if (colorMode == null) {
            colorMode = ColorMode.TEXTURE;
        }
        if (caveMode == null) {
            caveMode = CaveMode.AUTO;
        }
        if (waypointOcclusion == null) {
            waypointOcclusion = Occlusion.ALWAYS;
        }
        if (teleportCommand == null || teleportCommand.isBlank()) {
            teleportCommand = "tp @s {x} {y} {z}";
        }
        if (teleportCommand.startsWith("/")) {
            teleportCommand = teleportCommand.substring(1);
        }
        minimapZoom = clamp(minimapZoom, 0, MINIMAP_ZOOMS.length - 1);
        minimapOffsetX = clamp(minimapOffsetX, 0, 400);
        minimapOffsetY = clamp(minimapOffsetY, 0, 400);
        minimapSize = clamp(minimapSize, 60, 260);
        minimapOpacity = clamp(minimapOpacity, 20, 100);
        shadingStrength = clamp(shadingStrength, 0, 100);
        terrainQuality = clamp(terrainQuality, 25, 100);
        terrainVertical = Math.max(0.5, Math.min(4.0, terrainVertical));
        terrainFov = clamp(terrainFov, 30, 90);
        waypointMaxDistance = clamp(waypointMaxDistance, 0, 100000);
        waypointScale = clamp(waypointScale, 50, 300);
        deathLimit = clamp(deathLimit, 1, 50);
        deathExpiryHours = clamp(deathExpiryHours, 0, 720);
        temporaryExpiryMinutes = clamp(temporaryExpiryMinutes, 0, 1440);
        memoryLimitMb = clamp(memoryLimitMb, 64, 2048);
        workerThreads = clamp(workerThreads, 0, 8);
        scanRadius = clamp(scanRadius, 0, 32);
        saveIntervalSeconds = clamp(saveIntervalSeconds, 5, 600);
        return this;
    }

    public double minimapScale() {
        return MINIMAP_ZOOMS[clamp(minimapZoom, 0, MINIMAP_ZOOMS.length - 1)];
    }

    public int threads() {
        if (workerThreads > 0) {
            return workerThreads;
        }
        return clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 4);
    }

    private static int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    public static AtlasConfig load(Path file) {
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                AtlasConfig read = GSON.fromJson(reader, AtlasConfig.class);
                if (read != null) {
                    if (!text(file).contains("\"version\"")) {
                        read.waypointNames = false;
                        read.waypointDistance = false;
                    }
                    read.version = 2;
                    return read.sanitize();
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not read {}: {}", file, exception.getMessage());
            }
        }
        return new AtlasConfig();
    }

    private static String text(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not save {}: {}", file, exception.getMessage());
        }
    }
}
