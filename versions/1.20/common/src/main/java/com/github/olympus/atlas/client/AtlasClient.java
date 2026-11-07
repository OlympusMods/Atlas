package com.github.olympus.atlas.client;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AtlasClient {
    public static final String MOD_ID = "atlas";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Path configDirectory = Path.of("config");
    private static KeyBindings keys;

    private AtlasClient() {
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    public static void configure(Path directory) {
        configDirectory = directory;
    }

    public static Path configDirectory() {
        return configDirectory;
    }

    public static synchronized KeyBindings keys() {
        if (keys == null) {
            keys = KeyFactory.create();
        }
        return keys;
    }

    public static synchronized void installKeys(KeyBindings bindings) {
        keys = bindings;
    }

    public static void tick(Minecraft minecraft) {
        Atlas.tick(minecraft);
    }
}
