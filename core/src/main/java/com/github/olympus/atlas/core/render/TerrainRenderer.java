package com.github.olympus.atlas.core.render;

import com.github.olympus.atlas.core.map.Region;
import com.github.olympus.atlas.core.map.Tile;
import com.github.olympus.atlas.core.map.TileSource;
import java.util.Arrays;

public final class TerrainRenderer {
    private static final int TOP = Tile.LEVELS - 1;
    private static final int CACHE = 64;
    private static final Tile ABSENT = new Tile(Integer.MIN_VALUE, Integer.MIN_VALUE);
    private static final int MAX_STEPS = 640;

    public record Scene(Camera camera, double vertical, double far, boolean fog, int skyTop, int skyBottom,
            int ceiling) {
    }

    private final TileSource source;
    private final Tile[] cache = new Tile[CACHE];
    private final int[] cacheX = new int[CACHE];
    private final int[] cacheZ = new int[CACHE];
    private double hitT;
    private int hitColor;
    private double eyeX;
    private double eyeZ;
    private double eyeSpread;

    public TerrainRenderer(TileSource source) {
        this.source = source;
    }

    public void paint(Scene scene, int[] out, float[] depth, int width, int height, int rowFrom, int rowTo) {
        Camera camera = scene.camera();
        double vertical = scene.vertical();
        double ox = camera.x;
        double oy = camera.y / vertical;
        double oz = camera.z;
        double spread = 2.0 * camera.tanHalf / height;
        Arrays.fill(cache, null);
        for (int py = rowFrom; py < rowTo; py++) {
            double v = (1.0 - (py + 0.5) / height * 2.0) * camera.tanHalf;
            int row = py * width;
            for (int px = 0; px < width; px++) {
                double u = ((px + 0.5) / width * 2.0 - 1.0) * camera.tanHalf * camera.aspect;
                double dx = camera.fx + camera.rx * u + camera.ux * v;
                double dy = camera.fy + camera.uy * v;
                double dz = camera.fz + camera.rz * u + camera.uz * v;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= length;
                dy /= length;
                dz /= length;
                int sky = sky(scene, dy);
                int color = sky;
                float distance = Float.MAX_VALUE;
                if (trace(ox, oy, oz, dx, dy / vertical, dz, scene.far(), spread, scene.ceiling())) {
                    color = hitColor;
                    distance = (float) hitT;
                    if (scene.fog()) {
                        double amount = hitT / scene.far();
                        amount = amount * amount;
                        color = mix(color, sky, Math.min(1.0, amount));
                    }
                }
                out[row + px] = 0xFF000000 | color;
                if (depth != null) {
                    depth[row + px] = distance;
                }
            }
        }
    }

    private static int sky(Scene scene, double dy) {
        double t = Math.max(0.0, Math.min(1.0, 0.5 - dy * 1.2));
        return mix(scene.skyTop(), scene.skyBottom(), t);
    }

    private static int mix(int from, int to, double amount) {
        double keep = 1.0 - amount;
        int r = (int) (((from >> 16) & 0xFF) * keep + ((to >> 16) & 0xFF) * amount);
        int g = (int) (((from >> 8) & 0xFF) * keep + ((to >> 8) & 0xFF) * amount);
        int b = (int) ((from & 0xFF) * keep + (to & 0xFF) * amount);
        return (r << 16) | (g << 8) | b;
    }

    private Tile lookup(int rx, int rz) {
        int slot = ((rx & 7) << 3) | (rz & 7);
        Tile tile = cache[slot];
        if (tile != null && cacheX[slot] == rx && cacheZ[slot] == rz) {
            return tile == ABSENT ? null : tile;
        }
        double centerX = (rx << Region.BITS) + Region.SIZE / 2.0;
        double centerZ = (rz << Region.BITS) + Region.SIZE / 2.0;
        double nearX = Math.max(0.0, Math.abs(eyeX - centerX) - Region.SIZE / 2.0);
        double nearZ = Math.max(0.0, Math.abs(eyeZ - centerZ) - Region.SIZE / 2.0);
        tile = source.tile(rx, rz, detail(Math.sqrt(nearX * nearX + nearZ * nearZ) * eyeSpread));
        cache[slot] = tile == null ? ABSENT : tile;
        cacheX[slot] = rx;
        cacheZ[slot] = rz;
        return tile;
    }

    public boolean trace(double ox, double oy, double oz, double dx, double dy, double dz, double far,
            double spread, int ceiling) {
        if (Math.abs(dx) < 1.0e-9) {
            dx = 1.0e-9;
        }
        if (Math.abs(dz) < 1.0e-9) {
            dz = 1.0e-9;
        }
        double t = 0.0;
        if (oy > ceiling) {
            if (dy >= 0) {
                return false;
            }
            t = (ceiling - oy) / dy;
        }
        eyeX = ox;
        eyeZ = oz;
        eyeSpread = spread;
        int level = TOP;
        boolean sideX = false;
        for (int step = 0; step < MAX_STEPS && t <= far; step++) {
            double px = ox + dx * t;
            double pz = oz + dz * t;
            int bx = (int) Math.floor(px);
            int bz = (int) Math.floor(pz);
            int cx = bx >> level;
            int cz = bz >> level;
            int size = 1 << level;
            double edgeX = dx > 0 ? ((cx << level) + size - ox) / dx : ((cx << level) - ox) / dx;
            double edgeZ = dz > 0 ? ((cz << level) + size - oz) / dz : ((cz << level) - oz) / dz;
            double exit = Math.min(edgeX, edgeZ);
            Tile tile = lookup(bx >> Region.BITS, bz >> Region.BITS);
            int surface = Region.NONE;
            int have = level;
            if (tile != null) {
                have = Math.max(level, tile.minLevel);
                int bits = Region.BITS - have;
                short[] tops = tile.top[have];
                surface = tops[(((bz & Region.MASK) >> have) << bits) | ((bx & Region.MASK) >> have)];
            }
            double enterY = oy + dy * t;
            double exitY = oy + dy * exit;
            double high = surface + 1.0;
            if (surface == Region.NONE || Math.min(enterY, exitY) > high) {
                sideX = edgeX < edgeZ;
                t = exit + 1.0e-4 + exit * 1.0e-7;
                if (level < TOP) {
                    int parent = level + 1;
                    int nx = (int) Math.floor(ox + dx * t) >> parent;
                    int nz = (int) Math.floor(oz + dz * t) >> parent;
                    if (nx != (bx >> parent) || nz != (bz >> parent)) {
                        level = parent;
                    }
                }
                continue;
            }
            int detail = detail(t * spread);
            if (level > detail && level > (tile == null ? 0 : tile.minLevel)) {
                level--;
                continue;
            }
            int bits = Region.BITS - have;
            int color = tile.color[have][(((bz & Region.MASK) >> have) << bits) | ((bx & Region.MASK) >> have)];
            if ((color >>> 24) == 0) {
                sideX = edgeX < edgeZ;
                t = exit + 1.0e-4 + exit * 1.0e-7;
                continue;
            }
            if (enterY <= high) {
                hitT = t;
                hitColor = shade(color, sideX ? 0.74 : 0.6);
            } else {
                hitT = dy < 0 ? (high - oy) / dy : t;
                hitColor = color & 0xFFFFFF;
            }
            return true;
        }
        return false;
    }

    public double hitDistance() {
        return hitT;
    }

    private static int detail(double footprint) {
        int level = 0;
        while (level < 6 && footprint >= 1.6) {
            footprint *= 0.5;
            level++;
        }
        return level;
    }

    private static int shade(int color, double factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }
}
