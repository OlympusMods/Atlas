package com.github.olympus.atlas.core.waypoint;

import java.util.UUID;

public final class Waypoint {
    public enum Kind {
        NORMAL,
        DEATH,
        TEMPORARY
    }

    public static final int[] COLORS = {0xE5484D, 0xF2913D, 0xF5C542, 0x6CC24A, 0x2FB8A6, 0x3E9BF0, 0x7A6FF0,
        0xC65BD6, 0xEF6FA4, 0xFFFFFF, 0x9AA3AD, 0x8B5A2B};

    public String id = UUID.randomUUID().toString();
    public String name = "";
    public int x;
    public int y;
    public int z;
    public String dimension = "minecraft:overworld";
    public int color = COLORS[5];
    public String icon = "dot";
    public String group = "";
    public boolean visible = true;
    public boolean beam = true;
    public Kind kind = Kind.NORMAL;
    public long created = System.currentTimeMillis();

    public Waypoint copy() {
        Waypoint copy = new Waypoint();
        copy.id = id;
        copy.name = name;
        copy.x = x;
        copy.y = y;
        copy.z = z;
        copy.dimension = dimension;
        copy.color = color;
        copy.icon = icon;
        copy.group = group;
        copy.visible = visible;
        copy.beam = beam;
        copy.kind = kind;
        copy.created = created;
        return copy;
    }

    public Waypoint sanitize() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        if (name == null) {
            name = "";
        }
        if (dimension == null || dimension.isBlank()) {
            dimension = "minecraft:overworld";
        }
        if (icon == null || !Icons.exists(icon)) {
            icon = "dot";
        }
        if (group == null) {
            group = "";
        }
        if (kind == null) {
            kind = Kind.NORMAL;
        }
        color &= 0xFFFFFF;
        return this;
    }

    public double distance(double px, double py, double pz) {
        double dx = x + 0.5 - px;
        double dy = y - py;
        double dz = z + 0.5 - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
