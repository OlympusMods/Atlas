package com.github.olympus.atlas.core.map;

import com.github.olympus.atlas.core.AtlasConfig;

public record Style(boolean textures, boolean shading, int strength, boolean waterDepth, boolean biomeTint) {
    public static Style of(AtlasConfig config) {
        return new Style(config.colorMode == AtlasConfig.ColorMode.TEXTURE, config.heightShading,
                config.shadingStrength, config.waterDepth, config.biomeTint);
    }
}
