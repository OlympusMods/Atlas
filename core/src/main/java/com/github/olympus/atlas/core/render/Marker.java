package com.github.olympus.atlas.core.render;

public record Marker(float x, float y, int color, int kind, float size, float angle) {
    public static final int DOT = 0;
    public static final int DIAMOND = 1;
    public static final int ARROW = 2;
    public static final int SQUARE = 3;
}
