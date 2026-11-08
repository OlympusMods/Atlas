package com.github.olympus.atlas.client;

public final class Projection {
    private static volatile double fov = 70.0;

    private Projection() {
    }

    public static void fov(double value) {
        if (value > 1.0 && value < 179.0) {
            fov = value;
        }
    }

    public static double fov() {
        return fov;
    }
}
