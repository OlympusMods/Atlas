package com.github.olympus.atlas.core.waypoint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class Importers {
    private static final int[] XAERO_COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
        0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    public record Source(String kind, String label, Path path) {
    }

    private Importers() {
    }

    public static List<Source> find(Path gameDirectory) {
        List<Source> sources = new ArrayList<>();
        for (String folder : new String[] {"XaeroWaypoints", "xaero/minimap"}) {
            Path xaero = gameDirectory.resolve(folder);
            if (Files.isDirectory(xaero)) {
                for (Path world : folders(xaero)) {
                    sources.add(new Source("xaero", world.getFileName().toString(), world));
                }
            }
        }
        Path journey = gameDirectory.resolve("journeymap").resolve("data");
        for (String side : new String[] {"sp", "mp"}) {
            for (Path world : folders(journey.resolve(side))) {
                if (Files.isDirectory(world.resolve("waypoints"))) {
                    sources.add(new Source("journeymap", world.getFileName().toString(), world.resolve("waypoints")));
                }
            }
        }
        for (Path file : files(gameDirectory.resolve("voxelmap"))) {
            if (file.getFileName().toString().endsWith(".points")) {
                sources.add(new Source("voxelmap", file.getFileName().toString(), file));
            }
        }
        for (Path file : files(gameDirectory.resolve("atlas").resolve("import"))) {
            if (file.getFileName().toString().endsWith(".json")) {
                sources.add(new Source("atlas", file.getFileName().toString(), file));
            }
        }
        return sources;
    }

    public static List<Waypoint> load(Source source) {
        try {
            switch (source.kind()) {
                case "xaero":
                    return xaeroWorld(source.path());
                case "journeymap":
                    return journeyFolder(source.path());
                case "voxelmap":
                    return voxel(Files.readAllLines(source.path(), StandardCharsets.UTF_8));
                default:
                    WaypointStore.Document document = WaypointStore.read(source.path());
                    return document == null ? List.of() : document.waypoints;
            }
        } catch (IOException | RuntimeException exception) {
            return List.of();
        }
    }

    private static List<Path> folders(Path parent) {
        if (!Files.isDirectory(parent)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(parent)) {
            return stream.filter(Files::isDirectory).sorted().toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static List<Path> files(Path parent) {
        if (!Files.isDirectory(parent)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(parent)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private static List<Waypoint> xaeroWorld(Path world) throws IOException {
        List<Waypoint> waypoints = new ArrayList<>();
        for (Path folder : folders(world)) {
            String dimension = xaeroDimension(folder.getFileName().toString());
            for (Path file : files(folder)) {
                if (file.getFileName().toString().endsWith(".txt")) {
                    waypoints.addAll(xaero(Files.readAllLines(file, StandardCharsets.UTF_8), dimension));
                }
            }
        }
        return waypoints;
    }

    public static String xaeroDimension(String folder) {
        String name = folder.startsWith("dim%") ? folder.substring(4) : folder;
        switch (name) {
            case "0":
            case "overworld":
                return "minecraft:overworld";
            case "-1":
            case "the_nether":
                return "minecraft:the_nether";
            case "1":
            case "the_end":
                return "minecraft:the_end";
            default:
                return name.contains("$") ? name.replace('$', ':') : "minecraft:" + name;
        }
    }

    public static List<Waypoint> xaero(List<String> lines, String dimension) {
        List<Waypoint> waypoints = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.split(":");
            if (!line.startsWith("waypoint:") || parts.length < 9) {
                continue;
            }
            try {
                Waypoint waypoint = new Waypoint();
                waypoint.name = parts[1].replace("§§", ":");
                waypoint.x = Integer.parseInt(parts[3]);
                waypoint.y = parts[4].equals("~") ? 64 : Integer.parseInt(parts[4]);
                waypoint.z = Integer.parseInt(parts[5]);
                int color = Integer.parseInt(parts[6]);
                waypoint.color = XAERO_COLORS[Math.floorMod(color, XAERO_COLORS.length)];
                waypoint.visible = !Boolean.parseBoolean(parts[7]);
                int type = Integer.parseInt(parts[8]);
                if (type == 1 || type == 2) {
                    waypoint.kind = Waypoint.Kind.DEATH;
                    waypoint.icon = "skull";
                }
                if (parts.length > 9 && !parts[9].equals("gui.xaero_default")) {
                    waypoint.group = parts[9];
                }
                waypoint.dimension = dimension;
                waypoints.add(waypoint);
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return waypoints;
    }

    private static List<Waypoint> journeyFolder(Path folder) throws IOException {
        List<Waypoint> waypoints = new ArrayList<>();
        for (Path file : files(folder)) {
            if (file.getFileName().toString().endsWith(".json")) {
                Waypoint waypoint = journey(Files.readString(file, StandardCharsets.UTF_8));
                if (waypoint != null) {
                    waypoints.add(waypoint);
                }
            }
        }
        return waypoints;
    }

    public static Waypoint journey(String json) {
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            Waypoint waypoint = new Waypoint();
            waypoint.name = text(object, "name", "Waypoint");
            JsonObject position = object.has("pos") && object.get("pos").isJsonObject()
                    ? object.getAsJsonObject("pos") : object;
            waypoint.x = number(position, "x", 0);
            waypoint.y = number(position, "y", 64);
            waypoint.z = number(position, "z", 0);
            if (object.has("color") && object.get("color").isJsonPrimitive()) {
                waypoint.color = object.get("color").getAsInt() & 0xFFFFFF;
            } else {
                waypoint.color = (number(object, "r", 255) << 16) | (number(object, "g", 255) << 8)
                        | number(object, "b", 255);
            }
            waypoint.visible = !object.has("enable") || object.get("enable").getAsBoolean();
            if ("Death".equalsIgnoreCase(text(object, "type", ""))) {
                waypoint.kind = Waypoint.Kind.DEATH;
                waypoint.icon = "skull";
            }
            String dimension = text(position, "primaryDimension", "");
            if (dimension.isEmpty() && object.has("dimensions") && object.get("dimensions").isJsonArray()) {
                JsonArray dimensions = object.getAsJsonArray("dimensions");
                if (dimensions.size() > 0) {
                    dimension = dimensions.get(0).getAsString();
                }
            }
            waypoint.dimension = dimensionName(dimension);
            return waypoint;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String dimensionName(String raw) {
        switch (raw) {
            case "":
            case "0":
                return "minecraft:overworld";
            case "-1":
                return "minecraft:the_nether";
            case "1":
                return "minecraft:the_end";
            default:
                return raw.contains(":") ? raw : "minecraft:" + raw;
        }
    }

    private static String text(JsonObject object, String key, String fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static int number(JsonObject object, String key, int fallback) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? (int) Math.floor(value.getAsDouble()) : fallback;
    }

    public static List<Waypoint> voxel(List<String> lines) {
        List<Waypoint> waypoints = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("name:")) {
                continue;
            }
            Map<String, String> fields = new HashMap<>();
            for (String part : line.split(",")) {
                int colon = part.indexOf(':');
                if (colon > 0) {
                    fields.put(part.substring(0, colon), part.substring(colon + 1));
                }
            }
            try {
                Waypoint waypoint = new Waypoint();
                waypoint.name = fields.getOrDefault("name", "Waypoint").replace("~comma~", ",").replace("~colon~", ":");
                waypoint.x = Integer.parseInt(fields.getOrDefault("x", "0"));
                waypoint.y = Integer.parseInt(fields.getOrDefault("y", "64"));
                waypoint.z = Integer.parseInt(fields.getOrDefault("z", "0"));
                waypoint.visible = Boolean.parseBoolean(fields.getOrDefault("enabled", "true"));
                int red = (int) (Float.parseFloat(fields.getOrDefault("red", "1")) * 255f);
                int green = (int) (Float.parseFloat(fields.getOrDefault("green", "1")) * 255f);
                int blue = (int) (Float.parseFloat(fields.getOrDefault("blue", "1")) * 255f);
                waypoint.color = ((red & 0xFF) << 16) | ((green & 0xFF) << 8) | (blue & 0xFF);
                String dimension = fields.getOrDefault("dimensions", "overworld").split("#")[0]
                        .toLowerCase(Locale.ROOT);
                waypoint.dimension = dimensionName(dimension);
                if (waypoint.dimension.equals("minecraft:the_nether")) {
                    waypoint.x = Math.floorDiv(waypoint.x, 8);
                    waypoint.z = Math.floorDiv(waypoint.z, 8);
                }
                if ("skull".equals(fields.get("suffix"))) {
                    waypoint.kind = Waypoint.Kind.DEATH;
                    waypoint.icon = "skull";
                }
                waypoints.add(waypoint);
            } catch (NumberFormatException ignored) {
                continue;
            }
        }
        return waypoints;
    }
}
