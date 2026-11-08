package com.github.olympus.atlas.client;

import org.joml.Matrix4f;

public final class Projection {
    private static volatile double fov = 70.0;
    private static volatile double[] sway;

    private Projection() {
    }

    public static void fov(double value) {
        if (value > 1.0 && value < 179.0) {
            fov = value;
        }
        sway = null;
    }

    public static double fov() {
        return fov;
    }

    public static void sway(Matrix4f matrix, double aspect) {
        double[] rows = {matrix.m00(), matrix.m10(), matrix.m20(), matrix.m30(), matrix.m01(), matrix.m11(),
            matrix.m21(), matrix.m31(), matrix.m02(), matrix.m12(), matrix.m22(), matrix.m32()};
        if (Math.abs(matrix.m23()) > 0.5f) {
            double tan = Math.tan(Math.toRadians(fov) / 2.0);
            for (int i = 0; i < 4; i++) {
                rows[i] *= tan * aspect;
                rows[4 + i] *= tan;
            }
            rows[8] = -matrix.m03();
            rows[9] = -matrix.m13();
            rows[10] = -matrix.m23();
            rows[11] = -matrix.m33();
        }
        sway = rows;
    }

    public static double[] sway() {
        return sway;
    }
}
