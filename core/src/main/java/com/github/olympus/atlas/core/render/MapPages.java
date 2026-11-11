package com.github.olympus.atlas.core.render;

import com.github.olympus.atlas.core.map.Region;
import com.github.olympus.atlas.core.map.Tile;
import com.github.olympus.atlas.core.map.TileSource;
import java.util.Arrays;

public final class MapPages {
    public static final int SIZE = Region.SIZE;
    public static final int AREA = SIZE * SIZE;

    private static final long COORDINATE = 0x3FFFFFFFL;

    private MapPages() {
    }

    public static long key(int level, int px, int pz) {
        return ((long) level << 60) | ((px & COORDINATE) << 30) | (pz & COORDINATE);
    }

    public static double blocks(int level) {
        return (double) SIZE * (1 << level);
    }

    public static int first(double center, double extent, double scale, int level) {
        return (int) Math.floor((center - extent / 2.0 / scale) / blocks(level));
    }

    public static int last(double center, double extent, double scale, int level) {
        return (int) Math.floor((center + extent / 2.0 / scale) / blocks(level));
    }

    public static int edge(int page, int level, double center, double scale, double extent) {
        return (int) Math.round((page * blocks(level) - center) * scale + extent / 2.0);
    }

    public static void paint(TileSource source, int px, int pz, int level, int background, int[] out) {
        int span = 1 << level;
        int side = SIZE >> level;
        for (int iz = 0; iz < span; iz++) {
            for (int ix = 0; ix < span; ix++) {
                Tile tile = source.tile((px << level) + ix, (pz << level) + iz, level);
                int[] colors = null;
                int have = level;
                if (tile != null) {
                    have = Math.max(level, tile.minLevel);
                    colors = have < Tile.LEVELS ? tile.color[have] : null;
                }
                int left = ix * side;
                int top = iz * side;
                if (colors == null) {
                    for (int z = 0; z < side; z++) {
                        int row = (top + z) * SIZE + left;
                        Arrays.fill(out, row, row + side, background);
                    }
                    continue;
                }
                int shift = have - level;
                int bits = Region.BITS - have;
                for (int z = 0; z < side; z++) {
                    int row = (top + z) * SIZE + left;
                    int from = (z >> shift) << bits;
                    for (int x = 0; x < side; x++) {
                        int color = colors[from | (x >> shift)];
                        out[row + x] = (color >>> 24) == 0 ? background : color;
                    }
                }
            }
        }
    }
}
