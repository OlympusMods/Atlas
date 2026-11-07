package com.github.olympus.atlas.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.Level;

public final class Ids {
    private Ids() {
    }

    public static String dimension(Level level) {
        return level.dimension().identifier().toString();
    }

    public static InputStream open(String namespace, String path) throws IOException {
        Identifier location = Identifier.tryBuild(namespace, path);
        if (location == null) {
            return null;
        }
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        return resource.isPresent() ? resource.get().open() : null;
    }

    public static void register(String path, AbstractTexture texture) {
        Minecraft.getInstance().getTextureManager().register(Identifier.fromNamespaceAndPath(AtlasClient.MOD_ID, path), texture);
    }

    public static void release(String path) {
        Minecraft.getInstance().getTextureManager().release(Identifier.fromNamespaceAndPath(AtlasClient.MOD_ID, path));
    }
}
