package com.github.olympus.atlas.core.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

public final class MapWorld {
    public static final int SURFACE = Integer.MIN_VALUE;
    public static final int LAYER_STEP = 8;

    private static final long IDLE_NANOS = 20_000_000_000L;

    private final Path root;
    private final MapStore.Shared shared;
    private final Map<String, MapStore> stores = new ConcurrentHashMap<>();
    private final List<String> dimensions = new CopyOnWriteArrayList<>();

    public MapWorld(Path root, MapStore.Shared shared) {
        this.root = root;
        this.shared = shared;
        try {
            shared.io.execute(this::list);
        } catch (RuntimeException ignored) {
            return;
        }
    }

    private void list() {
        Path maps = root.resolve("map");
        if (!Files.isDirectory(maps)) {
            return;
        }
        try (Stream<Path> spaces = Files.list(maps)) {
            for (Path space : spaces.filter(Files::isDirectory).toList()) {
                try (Stream<Path> names = Files.list(space)) {
                    for (Path name : names.filter(Files::isDirectory).toList()) {
                        remember(space.getFileName() + ":" + name.getFileName().toString().replace("--", "/"));
                    }
                }
            }
        } catch (IOException ignored) {
            return;
        }
    }

    private void remember(String dimension) {
        if (!dimensions.contains(dimension)) {
            dimensions.add(dimension);
        }
    }

    public Path root() {
        return root;
    }

    public List<String> dimensions() {
        return new ArrayList<>(dimensions);
    }

    public static int layer(int y) {
        return Math.floorDiv(y, LAYER_STEP) * LAYER_STEP;
    }

    public MapStore store(String dimension, int layer) {
        remember(dimension);
        return stores.computeIfAbsent(dimension + "#" + layer, key -> {
            int colon = dimension.indexOf(':');
            String space = colon < 0 ? "minecraft" : dimension.substring(0, colon);
            String name = (colon < 0 ? dimension : dimension.substring(colon + 1)).replace("/", "--");
            String folder = layer == SURFACE ? "surface" : "y" + layer;
            return new MapStore(root.resolve("map").resolve(clean(space)).resolve(clean(name)).resolve(folder), shared);
        });
    }

    public static String clean(String name) {
        StringBuilder cleaned = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            cleaned.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.' ? c : '_');
        }
        String text = cleaned.toString();
        return text.isEmpty() || text.equals(".") || text.equals("..") ? "_" : text;
    }

    public void maintain(long saveIntervalNanos) {
        for (Map.Entry<String, MapStore> entry : stores.entrySet()) {
            MapStore store = entry.getValue();
            store.maintain(saveIntervalNanos);
            if (store.idleNanos() > IDLE_NANOS && stores.size() > 1) {
                stores.remove(entry.getKey());
                store.close();
            }
        }
    }

    public void restyle() {
        for (MapStore store : stores.values()) {
            store.restyle();
        }
    }

    public void close() {
        for (MapStore store : stores.values()) {
            store.close();
        }
        stores.clear();
    }
}
