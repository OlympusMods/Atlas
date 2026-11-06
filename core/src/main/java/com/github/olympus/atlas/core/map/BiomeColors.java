package com.github.olympus.atlas.core.map;

import java.util.Arrays;

public final class BiomeColors {
    public static final int GRASS = 0x91BD59;
    public static final int FOLIAGE = 0x77AB2F;
    public static final int WATER = 0x3F76E4;

    private volatile int[] grass = new int[0];
    private volatile int[] foliage = new int[0];
    private volatile int[] water = new int[0];
    private volatile boolean[] known = new boolean[0];

    public synchronized void define(int id, int grassColor, int foliageColor, int waterColor) {
        if (id >= grass.length) {
            int size = Math.max(id + 1, grass.length + 64);
            int[] grownGrass = Arrays.copyOf(grass, size);
            int[] grownFoliage = Arrays.copyOf(foliage, size);
            int[] grownWater = Arrays.copyOf(water, size);
            boolean[] grownKnown = Arrays.copyOf(known, size);
            known = grownKnown;
            water = grownWater;
            foliage = grownFoliage;
            grass = grownGrass;
        }
        grass[id] = grassColor & 0xFFFFFF;
        foliage[id] = foliageColor & 0xFFFFFF;
        water[id] = waterColor & 0xFFFFFF;
        known[id] = true;
    }

    public boolean known(int id) {
        boolean[] flags = known;
        return id < flags.length && flags[id];
    }

    public int grass(int id) {
        return known(id) ? grass[id] : GRASS;
    }

    public int foliage(int id) {
        return known(id) ? foliage[id] : FOLIAGE;
    }

    public int water(int id) {
        return known(id) ? water[id] : WATER;
    }
}
