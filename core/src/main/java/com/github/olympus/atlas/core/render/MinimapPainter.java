package com.github.olympus.atlas.core.render;

import java.util.List;

public final class MinimapPainter {
    private MinimapPainter() {
    }

    public static void markers(int[] pixels, int width, int height, List<Marker> markers) {
        for (Marker marker : markers) {
            shape(pixels, width, height, marker, marker.size() + 1.0f, 0xFF10131A);
            shape(pixels, width, height, marker, marker.size(), 0xFF000000 | marker.color());
        }
    }

    private static void shape(int[] pixels, int width, int height, Marker marker, float size, int color) {
        int x0 = Math.max(0, (int) Math.floor(marker.x() - size - 1));
        int y0 = Math.max(0, (int) Math.floor(marker.y() - size - 1));
        int x1 = Math.min(width - 1, (int) Math.ceil(marker.x() + size + 1));
        int y1 = Math.min(height - 1, (int) Math.ceil(marker.y() + size + 1));
        double cos = Math.cos(marker.angle());
        double sin = Math.sin(marker.angle());
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int covered = 0;
                for (int sample = 0; sample < 4; sample++) {
                    double dx = x + 0.25 + (sample & 1) * 0.5 - marker.x();
                    double dy = y + 0.25 + (sample >> 1) * 0.5 - marker.y();
                    if (inside(marker.kind(), dx, dy, size, cos, sin)) {
                        covered++;
                    }
                }
                if (covered > 0) {
                    int alpha = ((color >>> 24) * covered) >> 2;
                    int i = y * width + x;
                    pixels[i] = MapPainter.blend(pixels[i], (alpha << 24) | (color & 0xFFFFFF));
                }
            }
        }
    }

    private static boolean inside(int kind, double dx, double dy, double size, double cos, double sin) {
        switch (kind) {
            case Marker.DIAMOND:
                return Math.abs(dx) + Math.abs(dy) <= size;
            case Marker.SQUARE:
                return Math.abs(dx) <= size * 0.8 && Math.abs(dy) <= size * 0.8;
            case Marker.ARROW: {
                double lx = dx * cos + dy * sin;
                double ly = -dx * sin + dy * cos;
                if (ly < -size || ly > size * 0.75) {
                    return false;
                }
                double half = (ly + size) / (size * 1.75) * size * 0.8;
                if (Math.abs(lx) > half) {
                    return false;
                }
                return !(ly > size * 0.35 && Math.abs(lx) < (ly - size * 0.35) / (size * 0.4) * half);
            }
            default:
                return dx * dx + dy * dy <= size * size;
        }
    }

    public static void frame(int[] pixels, int size, boolean round, int opacity, int border, float thickness) {
        double center = size / 2.0;
        double radius = size / 2.0 - 0.5;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                double edge;
                if (round) {
                    double dx = x + 0.5 - center;
                    double dy = y + 0.5 - center;
                    edge = radius - Math.sqrt(dx * dx + dy * dy);
                } else {
                    edge = Math.min(Math.min(x + 0.5, y + 0.5), Math.min(size - x - 0.5, size - y - 0.5));
                }
                if (edge <= 0.0) {
                    pixels[i] = 0;
                    continue;
                }
                int color = pixels[i];
                int alpha = ((color >>> 24) * opacity) / 100;
                color = (alpha << 24) | (color & 0xFFFFFF);
                if (edge < thickness) {
                    double cover = Math.min(1.0, thickness - edge);
                    color = MapPainter.blend(color, ((int) (cover * (border >>> 24)) << 24) | (border & 0xFFFFFF));
                    if (edge < 1.0) {
                        color = ((int) ((color >>> 24) * edge) << 24) | (color & 0xFFFFFF);
                    }
                }
                pixels[i] = color;
            }
        }
    }
}
