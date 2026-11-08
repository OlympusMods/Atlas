package com.github.olympus.atlas.client;

import com.github.olympus.atlas.core.map.MapStore;
import com.github.olympus.atlas.core.map.Palette;
import com.github.olympus.atlas.mixin.NativeImageAccessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

public final class BlockColorLoader {
    private static final String[] FACES = {"top", "up", "end", "all", "texture", "cross", "plant", "particle", "side",
        "layer0"};

    private final Map<String, Integer> averages = new HashMap<>();

    private static final class Model {
        final Map<String, String> textures = new HashMap<>();
        boolean tinted;
    }

    public static void load(List<String> blocks, MapStore.Shared shared) {
        BlockColorLoader loader = new BlockColorLoader();
        int found = 0;
        for (String block : blocks) {
            try {
                if (loader.resolve(block, shared)) {
                    found++;
                }
            } catch (IOException | RuntimeException ignored) {
                continue;
            }
        }
        shared.palette.changed();
        AtlasClient.LOGGER.debug("Atlas read colors for {} of {} blocks", found, blocks.size());
    }

    private boolean resolve(String block, MapStore.Shared shared) throws IOException {
        int colon = block.indexOf(':');
        String namespace = block.substring(0, colon);
        String name = block.substring(colon + 1);
        JsonObject state = json(namespace, "blockstates/" + name + ".json");
        if (state == null) {
            return false;
        }
        String modelName = firstModel(state);
        if (modelName == null) {
            return false;
        }
        Model model = new Model();
        collect(modelName, model, 0);
        String texture = pick(model);
        if (texture == null) {
            return false;
        }
        Integer color = average(texture);
        if (color == null) {
            return false;
        }
        byte kind = Palette.TINT_NONE;
        int fixed = 0xFFFFFF;
        if (name.equals("spruce_leaves")) {
            kind = Palette.TINT_FIXED;
            fixed = 0x619961;
        } else if (name.equals("birch_leaves")) {
            kind = Palette.TINT_FIXED;
            fixed = 0x80A755;
        } else if (name.equals("lily_pad")) {
            kind = Palette.TINT_FIXED;
            fixed = 0x208030;
        } else if (name.equals("redstone_wire")) {
            kind = Palette.TINT_FIXED;
            fixed = 0xC01000;
        } else if (name.endsWith("_stem")) {
            kind = Palette.TINT_FIXED;
            fixed = 0x7DB51A;
        } else if (model.tinted) {
            kind = name.contains("leaves") || name.contains("vine") ? Palette.TINT_FOLIAGE : Palette.TINT_GRASS;
        }
        shared.palette.texture(shared.blocks.id(block), color, kind, fixed);
        return true;
    }

    private static JsonObject json(String namespace, String path) throws IOException {
        InputStream stream = Ids.open(namespace, path);
        if (stream == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        }
    }

    private static String firstModel(JsonObject state) {
        if (state.has("variants") && state.get("variants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : state.getAsJsonObject("variants").entrySet()) {
                String model = modelOf(entry.getValue());
                if (model != null) {
                    return model;
                }
            }
        }
        if (state.has("multipart") && state.get("multipart").isJsonArray()) {
            for (JsonElement part : state.getAsJsonArray("multipart")) {
                if (part.isJsonObject() && part.getAsJsonObject().has("apply")) {
                    String model = modelOf(part.getAsJsonObject().get("apply"));
                    if (model != null) {
                        return model;
                    }
                }
            }
        }
        return null;
    }

    private static String modelOf(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            return array.size() == 0 ? null : modelOf(array.get(0));
        }
        if (element.isJsonObject() && element.getAsJsonObject().has("model")) {
            return element.getAsJsonObject().get("model").getAsString();
        }
        return null;
    }

    private static void collect(String name, Model model, int depth) throws IOException {
        if (depth > 8) {
            return;
        }
        int colon = name.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : name.substring(0, colon);
        String path = colon < 0 ? name : name.substring(colon + 1);
        JsonObject json = json(namespace, "models/" + path + ".json");
        if (json == null) {
            return;
        }
        if (json.has("textures") && json.get("textures").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("textures").entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    model.textures.putIfAbsent(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
        if (json.has("elements") && json.get("elements").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("elements")) {
                if (!element.isJsonObject() || !element.getAsJsonObject().has("faces")) {
                    continue;
                }
                JsonElement faces = element.getAsJsonObject().get("faces");
                if (!faces.isJsonObject()) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> face : faces.getAsJsonObject().entrySet()) {
                    if (face.getValue().isJsonObject() && face.getValue().getAsJsonObject().has("tintindex")) {
                        model.tinted = true;
                    }
                }
            }
        }
        if (json.has("parent") && json.get("parent").isJsonPrimitive()) {
            collect(json.get("parent").getAsString(), model, depth + 1);
        }
    }

    private static String pick(Model model) {
        for (String face : FACES) {
            String texture = follow(model, face, 0);
            if (texture != null) {
                return texture;
            }
        }
        for (String key : model.textures.keySet()) {
            String texture = follow(model, key, 0);
            if (texture != null) {
                return texture;
            }
        }
        return null;
    }

    private static String follow(Model model, String key, int depth) {
        String value = model.textures.get(key);
        if (value == null || depth > 8) {
            return null;
        }
        return value.startsWith("#") ? follow(model, value.substring(1), depth + 1) : value;
    }

    private Integer average(String texture) throws IOException {
        if (averages.containsKey(texture)) {
            return averages.get(texture);
        }
        int colon = texture.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : texture.substring(0, colon);
        String path = colon < 0 ? texture : texture.substring(colon + 1);
        Integer result = null;
        InputStream stream = Ids.open(namespace, "textures/" + path + ".png");
        if (stream != null) {
            try (InputStream input = stream; NativeImage image = NativeImage.read(input)) {
                long pointer = ((NativeImageAccessor) (Object) image).atlas$pixels();
                int count = image.getWidth() * image.getHeight();
                long red = 0;
                long green = 0;
                long blue = 0;
                long weight = 0;
                for (int i = 0; pointer != 0L && i < count; i++) {
                    int pixel = MemoryUtil.memGetInt(pointer + i * 4L);
                    int alpha = pixel >>> 24;
                    if (alpha < 16) {
                        continue;
                    }
                    red += (long) (pixel & 0xFF) * alpha;
                    green += (long) ((pixel >> 8) & 0xFF) * alpha;
                    blue += (long) ((pixel >> 16) & 0xFF) * alpha;
                    weight += alpha;
                }
                if (weight > 0) {
                    result = (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
                }
            }
        }
        averages.put(texture, result);
        return result;
    }
}
