package com.github.olympus.atlas.core.render;

import com.github.olympus.atlas.core.map.Region;
import com.github.olympus.atlas.core.map.Tile;
import com.github.olympus.atlas.core.map.TileSource;

public final class MapPainter {
    private MapPainter() {
    }

    public record View(double centerX, double centerZ, double scale, double rotation, int background, boolean grid) {
    }

    public static int level(double scale) {
        int level = 0;
        double size = 1.0;
        while (level < Tile.LEVELS - 1 && size * 2.0 * scale <= 1.0) {
            size *= 2.0;
            level++;
        }
        return level;
    }

    public static void paint(TileSource source, View view, int[] out, int width, int height, int rowFrom, int rowTo) {
        int level = level(view.scale());
        int bits = Region.BITS - level;
        int mask = (1 << bits) - 1;
        double inverse = 1.0 / view.scale();
        double cos = Math.cos(view.rotation()) * inverse;
        double sin = Math.sin(view.rotation()) * inverse;
        double halfW = width / 2.0;
        double halfH = height / 2.0;
        boolean grid = view.grid() && view.rotation() == 0.0;
        int gridStep = view.scale() >= 0.75 ? 16 : view.scale() >= 0.04 ? Region.SIZE : 0;
        int background = view.background();
        Tile tile = null;
        int tileX = Integer.MIN_VALUE;
        int tileZ = Integer.MIN_VALUE;
        int[] colors = null;
        int shift = 0;
        for (int py = rowFrom; py < rowTo; py++) {
            double dy = py + 0.5 - halfH;
            double wx = view.centerX() + (0.5 - halfW) * cos - dy * sin;
            double wz = view.centerZ() + (0.5 - halfW) * sin + dy * cos;
            int row = py * width;
            int lastX = Integer.MIN_VALUE;
            int lastRowZ = (int) Math.floor(wz - cos);
            for (int px = 0; px < width; px++) {
                int bx = (int) Math.floor(wx);
                int bz = (int) Math.floor(wz);
                int rx = bx >> Region.BITS;
                int rz = bz >> Region.BITS;
                if (rx != tileX || rz != tileZ) {
                    tileX = rx;
                    tileZ = rz;
                    tile = source.tile(rx, rz, level);
                    colors = null;
                    if (tile != null) {
                        int have = Math.max(level, tile.minLevel);
                        colors = tile.color[have];
                        shift = have - level;
                    }
                }
                int color = 0;
                if (colors != null) {
                    int cx = ((bx & Region.MASK) >> level) >> shift;
                    int cz = ((bz & Region.MASK) >> level) >> shift;
                    color = colors[(cz << (bits - shift)) | cx];
                }
                if ((color >>> 24) == 0) {
                    color = background;
                }
                if (grid && gridStep != 0) {
                    boolean line = (bx != lastX && Math.floorMod(bx, gridStep) == 0 && px > 0)
                            || (bz != lastRowZ && Math.floorMod(bz, gridStep) == 0);
                    if (line) {
                        color = blend(color, 0x40000000);
                    }
                }
                lastX = bx;
                out[row + px] = color;
                wx += cos;
                wz += sin;
            }
        }
    }

    public static int blend(int under, int over) {
        int alpha = over >>> 24;
        if (alpha == 0) {
            return under;
        }
        int keep = 255 - alpha;
        int underAlpha = under >>> 24;
        int r = (((under >> 16) & 0xFF) * keep + ((over >> 16) & 0xFF) * alpha) / 255;
        int g = (((under >> 8) & 0xFF) * keep + ((over >> 8) & 0xFF) * alpha) / 255;
        int b = ((under & 0xFF) * keep + (over & 0xFF) * alpha) / 255;
        int a = Math.min(255, underAlpha + alpha * (255 - underAlpha) / 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
