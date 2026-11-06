package com.github.olympus.atlas.core.map;

public final class Tile {
    public static final int LEVELS = Region.BITS + 1;

    public final int rx;
    public final int rz;
    public final int[][] color = new int[LEVELS][];
    public final short[][] top = new short[LEVELS][];
    public volatile int minLevel;
    public volatile int style;

    public Tile(int rx, int rz) {
        this.rx = rx;
        this.rz = rz;
        for (int level = 0; level < LEVELS; level++) {
            int side = Region.SIZE >> level;
            color[level] = new int[side * side];
            top[level] = new short[side * side];
        }
    }

    public void trim(int level) {
        int keep = Math.min(level, LEVELS - 1);
        for (int i = 0; i < keep; i++) {
            color[i] = null;
            top[i] = null;
        }
        if (keep > minLevel) {
            minLevel = keep;
        }
    }

    public long bytes() {
        long total = 0;
        for (int level = minLevel; level < LEVELS; level++) {
            long side = Region.SIZE >> level;
            total += side * side * 6;
        }
        return total;
    }

    public int height(int x, int z) {
        short[] exact = top[0];
        return exact == null ? Region.NONE : exact[((z & Region.MASK) << Region.BITS) | (x & Region.MASK)];
    }
}
