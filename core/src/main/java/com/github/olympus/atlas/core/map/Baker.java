package com.github.olympus.atlas.core.map;

public final class Baker {
    private Baker() {
    }

    public static boolean bake(Region region, Tile tile, int x0, int z0, int x1, int z1, Style style, Palette palette,
            BiomeColors biomes, Tile north, Tile west) {
        int[] color = tile.color[0];
        short[] top = tile.top[0];
        float strength = style.strength() / 100f;
        boolean patched = false;
        for (int z = z0; z < z1; z++) {
            for (int x = x0; x < x1; x++) {
                int i = (z << Region.BITS) | x;
                short h = region.height[i];
                top[i] = h;
                if (h == Region.NONE) {
                    color[i] = 0;
                    continue;
                }
                int id = region.block[i] & 0xFFFF;
                int biome = region.biome[i] & 0xFFFF;
                int rgb = surface(id, biome, style, palette, biomes);
                if (palette.water(id)) {
                    int depth = region.depth[i] & 0xFF;
                    if (style.waterDepth()) {
                        int under = region.under[i] & 0xFFFF;
                        if (under != 0) {
                            int floor = scale(surface(under, biome, style, palette, biomes),
                                    Math.max(0.3f, 1f - depth * 0.06f));
                            rgb = mix(floor, rgb, Math.min(0.96f, 0.45f + depth * 0.06f));
                        } else {
                            rgb = scale(rgb, Math.max(0.7f, 1f - depth * 0.02f));
                        }
                    }
                } else if (style.shading()) {
                    short hn;
                    if (z > 0) {
                        hn = region.height[i - Region.SIZE];
                    } else {
                        hn = region.north[x];
                        if (hn == Region.NONE && north != null) {
                            hn = (short) north.height(x, Region.MASK);
                            if (hn != Region.NONE) {
                                region.north[x] = hn;
                                patched = true;
                            }
                        }
                    }
                    short hw;
                    if (x > 0) {
                        hw = region.height[i - 1];
                    } else {
                        hw = region.west[z];
                        if (hw == Region.NONE && west != null) {
                            hw = (short) west.height(Region.MASK, z);
                            if (hw != Region.NONE) {
                                region.west[z] = hw;
                                patched = true;
                            }
                        }
                    }
                    int slope = (hn == Region.NONE ? 0 : h - hn) + (hw == Region.NONE ? 0 : h - hw);
                    slope = Math.max(-3, Math.min(3, slope));
                    float factor = 1f + strength * (slope * 0.09f + (h - 64) / 384f * 0.35f);
                    rgb = scale(rgb, Math.max(0.5f, Math.min(1.4f, factor)));
                }
                if ((region.light[i] & Region.CUT) != 0) {
                    rgb = scale(rgb, 0.32f);
                }
                color[i] = 0xFF000000 | rgb;
            }
        }
        return patched;
    }

    public static boolean missingApron(Region region) {
        for (int i = 0; i < Region.SIZE; i++) {
            if ((region.north[i] == Region.NONE && region.height[i] != Region.NONE)
                    || (region.west[i] == Region.NONE && region.height[i << Region.BITS] != Region.NONE)) {
                return true;
            }
        }
        return false;
    }

    private static int surface(int id, int biome, Style style, Palette palette, BiomeColors biomes) {
        byte kind = palette.tint(id);
        if (style.textures() && palette.textured(id)) {
            int color = palette.texture(id);
            if (kind == Palette.TINT_FIXED) {
                return multiply(color, palette.fixed(id));
            }
            if (kind != Palette.TINT_NONE) {
                return multiply(color, tint(kind, style.biomeTint() ? biome : -1, biomes));
            }
            return color;
        }
        int color = palette.map(id);
        if (style.biomeTint() && kind != Palette.TINT_NONE && kind != Palette.TINT_FIXED) {
            int tint = tint(kind, biome, biomes);
            if (kind == Palette.TINT_WATER) {
                return tint;
            }
            return scale(tint, kind == Palette.TINT_GRASS ? 0.85f : 0.7f);
        }
        return color;
    }

    private static int tint(byte kind, int biome, BiomeColors biomes) {
        if (biome < 0) {
            return kind == Palette.TINT_GRASS ? BiomeColors.GRASS
                    : kind == Palette.TINT_FOLIAGE ? BiomeColors.FOLIAGE : BiomeColors.WATER;
        }
        return kind == Palette.TINT_GRASS ? biomes.grass(biome)
                : kind == Palette.TINT_FOLIAGE ? biomes.foliage(biome) : biomes.water(biome);
    }

    public static void mip(Tile tile, int x0, int z0, int x1, int z1) {
        for (int level = 1; level < Tile.LEVELS; level++) {
            int side = Region.SIZE >> level;
            int below = side << 1;
            int[] color = tile.color[level];
            int[] fine = tile.color[level - 1];
            short[] top = tile.top[level];
            short[] fineTop = tile.top[level - 1];
            int cx0 = x0 >> level;
            int cz0 = z0 >> level;
            int cx1 = (x1 - 1) >> level;
            int cz1 = (z1 - 1) >> level;
            for (int z = cz0; z <= cz1; z++) {
                for (int x = cx0; x <= cx1; x++) {
                    int a = (z << 1) * below + (x << 1);
                    int r = 0;
                    int g = 0;
                    int b = 0;
                    int n = 0;
                    short high = Region.NONE;
                    for (int k = 0; k < 4; k++) {
                        int j = a + (k & 1) + (k >> 1) * below;
                        int c = fine[j];
                        if ((c >>> 24) != 0) {
                            r += (c >> 16) & 0xFF;
                            g += (c >> 8) & 0xFF;
                            b += c & 0xFF;
                            n++;
                        }
                        if (fineTop[j] > high) {
                            high = fineTop[j];
                        }
                    }
                    int i = z * side + x;
                    color[i] = n == 0 ? 0 : 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (b / n);
                    top[i] = high;
                }
            }
        }
    }

    public static int scale(int rgb, float factor) {
        int r = Math.min(255, (int) (((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    public static int multiply(int a, int b) {
        int r = ((a >> 16) & 0xFF) * ((b >> 16) & 0xFF) / 255;
        int g = ((a >> 8) & 0xFF) * ((b >> 8) & 0xFF) / 255;
        int bl = (a & 0xFF) * (b & 0xFF) / 255;
        return (r << 16) | (g << 8) | bl;
    }

    public static int mix(int from, int to, float amount) {
        float keep = 1f - amount;
        int r = (int) (((from >> 16) & 0xFF) * keep + ((to >> 16) & 0xFF) * amount);
        int g = (int) (((from >> 8) & 0xFF) * keep + ((to >> 8) & 0xFF) * amount);
        int b = (int) ((from & 0xFF) * keep + (to & 0xFF) * amount);
        return (r << 16) | (g << 8) | b;
    }
}
