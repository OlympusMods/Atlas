package com.github.olympus.atlas.client;

import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

public final class Heights {
    private Heights() {
    }

    public static int minY(Level level) {
        return level.getMinBuildHeight();
    }

    public static int maxY(Level level) {
        return level.getMaxBuildHeight();
    }

    public static void biomes(Level level, BiConsumer<String, Biome> each) {
        for (Map.Entry<ResourceKey<Biome>, Biome> entry : level.registryAccess().registryOrThrow(Registries.BIOME)
                .entrySet()) {
            each.accept(World.keyName(entry.getKey()), entry.getValue());
        }
    }
}
