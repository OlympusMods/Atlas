package com.github.olympus.atlas.core.render;

public final class BeamShape {
    public static final float HEIGHT = 1024f;
    public static final float CORE = 0.2f;
    public static final float GLOW = 0.25f;
    public static final float GLOW_ALPHA = 0.125f;
    public static final float PERIOD = 40f;

    public interface Sink {
        void vertex(float x, float y, float z, float u, float v);
    }

    private BeamShape() {
    }

    public static void core(Sink sink, float time) {
        double angle = Math.toRadians(time * 2.25f - 45f);
        float cos = (float) Math.cos(angle) * CORE;
        float sin = (float) Math.sin(angle) * CORE;
        float low = scroll(time);
        float high = HEIGHT * (0.5f / CORE) + low;
        box(sink, sin, cos, cos, -sin, -cos, sin, -sin, -cos, high, low);
    }

    public static void glow(Sink sink, float time) {
        float low = scroll(time);
        box(sink, -GLOW, -GLOW, GLOW, -GLOW, -GLOW, GLOW, GLOW, GLOW, HEIGHT + low, low);
    }

    public static float scroll(float time) {
        float along = -time;
        float value = along * 0.2f - (float) Math.floor(along * 0.1f);
        return -1f + value - (float) Math.floor(value);
    }

    public static float fade(double flat, double distance, double limit) {
        float alpha = (float) Math.max(0.12, Math.min(1.0, (flat - 2.0) / 10.0));
        if (limit > 0) {
            if (distance > limit) {
                return 0f;
            }
            double from = limit * 0.8;
            if (distance > from) {
                alpha *= (float) (1.0 - (distance - from) / (limit - from));
            }
        }
        return alpha;
    }

    private static void box(Sink sink, float ax, float az, float bx, float bz, float cx, float cz, float dx,
            float dz, float high, float low) {
        side(sink, ax, az, bx, bz, high, low);
        side(sink, dx, dz, cx, cz, high, low);
        side(sink, bx, bz, dx, dz, high, low);
        side(sink, cx, cz, ax, az, high, low);
    }

    private static void side(Sink sink, float x1, float z1, float x2, float z2, float high, float low) {
        sink.vertex(x1, HEIGHT, z1, 1f, high);
        sink.vertex(x1, 0f, z1, 1f, low);
        sink.vertex(x2, 0f, z2, 0f, low);
        sink.vertex(x2, HEIGHT, z2, 0f, high);
    }
}
