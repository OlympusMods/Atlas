package com.github.olympus.atlas.core.map;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public final class Palette {
    public static final byte TINT_NONE = 0;
    public static final byte TINT_GRASS = 1;
    public static final byte TINT_FOLIAGE = 2;
    public static final byte TINT_WATER = 3;
    public static final byte TINT_FIXED = 4;

    private static final int UNKNOWN = 0x8C8C8C;

    private final AtomicInteger version = new AtomicInteger();
    private volatile int[] map = new int[0];
    private volatile int[] texture = new int[0];
    private volatile int[] fixed = new int[0];
    private volatile byte[] tint = new byte[0];
    private volatile boolean[] water = new boolean[0];
    private volatile boolean[] textured = new boolean[0];

    private synchronized void ensure(int id) {
        if (id < map.length) {
            return;
        }
        int size = Math.max(id + 1, map.length + 256);
        int[] grownMap = Arrays.copyOf(map, size);
        Arrays.fill(grownMap, map.length, size, UNKNOWN);
        int[] grownTexture = Arrays.copyOf(texture, size);
        fixed = Arrays.copyOf(fixed, size);
        tint = Arrays.copyOf(tint, size);
        water = Arrays.copyOf(water, size);
        textured = Arrays.copyOf(textured, size);
        texture = grownTexture;
        map = grownMap;
    }

    public synchronized void define(int id, int mapColor, boolean isWater) {
        ensure(id);
        map[id] = mapColor & 0xFFFFFF;
        water[id] = isWater;
        if (isWater) {
            tint[id] = TINT_WATER;
        }
        version.incrementAndGet();
    }

    public synchronized void texture(int id, int color, byte tintKind, int fixedColor) {
        ensure(id);
        texture[id] = color & 0xFFFFFF;
        textured[id] = true;
        tint[id] = water[id] ? TINT_WATER : tintKind;
        fixed[id] = fixedColor & 0xFFFFFF;
    }

    public void changed() {
        version.incrementAndGet();
    }

    public int version() {
        return version.get();
    }

    public int map(int id) {
        int[] colors = map;
        return id < colors.length ? colors[id] : UNKNOWN;
    }

    public int texture(int id) {
        boolean[] has = textured;
        if (id < has.length && has[id]) {
            return texture[id];
        }
        return map(id);
    }

    public boolean textured(int id) {
        boolean[] has = textured;
        return id < has.length && has[id];
    }

    public byte tint(int id) {
        byte[] kinds = tint;
        return id < kinds.length ? kinds[id] : TINT_NONE;
    }

    public int fixed(int id) {
        int[] colors = fixed;
        return id < colors.length ? colors[id] : 0xFFFFFF;
    }

    public boolean water(int id) {
        boolean[] flags = water;
        return id < flags.length && flags[id];
    }
}
