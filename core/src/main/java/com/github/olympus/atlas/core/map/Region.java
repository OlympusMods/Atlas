package com.github.olympus.atlas.core.map;

import java.util.Arrays;

public final class Region {
    public static final int BITS = 9;
    public static final int SIZE = 1 << BITS;
    public static final int MASK = SIZE - 1;
    public static final int AREA = SIZE * SIZE;
    public static final short NONE = Short.MIN_VALUE;
    public static final int CUT = 0x10;
    public static final long BYTES = AREA * 10L + SIZE * 4L;

    public final int rx;
    public final int rz;
    public final short[] height = new short[AREA];
    public final short[] block = new short[AREA];
    public final short[] under = new short[AREA];
    public final short[] biome = new short[AREA];
    public final byte[] depth = new byte[AREA];
    public final byte[] light = new byte[AREA];
    public final short[] north = new short[SIZE];
    public final short[] west = new short[SIZE];

    public Region(int rx, int rz) {
        this.rx = rx;
        this.rz = rz;
        Arrays.fill(height, NONE);
        Arrays.fill(north, NONE);
        Arrays.fill(west, NONE);
    }

    public static long key(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    public boolean apply(ChunkData chunk) {
        int baseX = (chunk.cx << 4) & MASK;
        int baseZ = (chunk.cz << 4) & MASK;
        boolean changed = false;
        for (int z = 0; z < 16; z++) {
            int from = z << 4;
            int to = ((baseZ + z) << BITS) | baseX;
            if (!changed && same(chunk, from, to)) {
                continue;
            }
            changed = true;
            System.arraycopy(chunk.height, from, height, to, 16);
            System.arraycopy(chunk.block, from, block, to, 16);
            System.arraycopy(chunk.under, from, under, to, 16);
            System.arraycopy(chunk.biome, from, biome, to, 16);
            System.arraycopy(chunk.depth, from, depth, to, 16);
            System.arraycopy(chunk.light, from, light, to, 16);
        }
        return changed;
    }

    private boolean same(ChunkData chunk, int from, int to) {
        for (int x = 0; x < 16; x++) {
            if (chunk.height[from + x] != height[to + x] || chunk.block[from + x] != block[to + x]
                    || chunk.under[from + x] != under[to + x] || chunk.biome[from + x] != biome[to + x]
                    || chunk.depth[from + x] != depth[to + x] || chunk.light[from + x] != light[to + x]) {
                return false;
            }
        }
        return true;
    }

    public boolean empty() {
        for (short value : height) {
            if (value != NONE) {
                return false;
            }
        }
        return true;
    }
}
