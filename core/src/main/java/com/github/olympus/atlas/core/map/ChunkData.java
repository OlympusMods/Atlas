package com.github.olympus.atlas.core.map;

import java.util.Arrays;

public final class ChunkData {
    public static final int AREA = 256;

    public final int cx;
    public final int cz;
    public final short[] height = new short[AREA];
    public final short[] block = new short[AREA];
    public final short[] under = new short[AREA];
    public final short[] biome = new short[AREA];
    public final byte[] depth = new byte[AREA];
    public final byte[] light = new byte[AREA];

    public ChunkData(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
        Arrays.fill(height, Region.NONE);
    }
}
