package com.github.olympus.atlas.client;

import com.github.olympus.atlas.mixin.NativeImageAccessor;
import com.github.olympus.proteus.client.ui.Canvas;
import com.mojang.blaze3d.platform.NativeImage;
import java.lang.reflect.Constructor;
import java.util.function.Supplier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.system.MemoryUtil;

public final class MapTexture {
    private final String path;
    private DynamicTexture texture;
    private NativeImage image;
    private int width;
    private int height;
    private boolean failed;

    public MapTexture(String name) {
        this.path = "map/" + name;
    }

    public boolean ready() {
        return texture != null;
    }

    public boolean failed() {
        return failed;
    }

    public void upload(int[] abgr, int w, int h) {
        if (failed) {
            return;
        }
        try {
            if (texture == null || w != width || h != height) {
                close();
                image = new NativeImage(w, h, false);
                texture = create(image);
                width = w;
                height = h;
                Ids.register(path, texture);
            }
            long pointer = ((NativeImageAccessor) (Object) image).atlas$pixels();
            if (pointer == 0L) {
                return;
            }
            MemoryUtil.memIntBuffer(pointer, w * h).put(abgr, 0, w * h);
            texture.upload();
        } catch (ReflectiveOperationException | RuntimeException exception) {
            failed = true;
            AtlasClient.LOGGER.warn("Atlas could not create a map texture", exception);
        }
    }

    private static DynamicTexture create(NativeImage image) throws ReflectiveOperationException {
        for (Constructor<?> constructor : DynamicTexture.class.getConstructors()) {
            Class<?>[] types = constructor.getParameterTypes();
            if (types.length == 1 && types[0] == NativeImage.class) {
                return (DynamicTexture) constructor.newInstance(image);
            }
            if (types.length == 2 && types[0] == Supplier.class && types[1] == NativeImage.class) {
                Supplier<String> label = () -> "atlas map";
                return (DynamicTexture) constructor.newInstance(label, image);
            }
        }
        throw new NoSuchMethodException("DynamicTexture constructor");
    }

    public void draw(Canvas canvas, float x, float y, float w, float h) {
        if (texture == null) {
            return;
        }
        Blend.on();
        canvas.push();
        canvas.translate(x, y);
        canvas.scale(w / 32f, h / 32f);
        canvas.tiled(AtlasClient.MOD_ID, path, 0, 0, 32, 32);
        canvas.pop();
    }

    public void close() {
        if (texture != null) {
            Ids.release(path);
            texture = null;
            image = null;
        }
    }

    public static void abgr(int[] pixels, int from, int to) {
        for (int i = from; i < to; i++) {
            int c = pixels[i];
            pixels[i] = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
        }
    }
}
