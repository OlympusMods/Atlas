package com.github.olympus.atlas.client;

import java.util.List;
import net.minecraft.client.KeyMapping;

public final class KeyBindings {
    public final KeyMapping map;
    public final KeyMapping waypoint;
    public final KeyMapping temporary;
    public final KeyMapping minimap;
    public final KeyMapping zoomIn;
    public final KeyMapping zoomOut;
    public final KeyMapping view;
    public final KeyMapping cave;
    public final KeyMapping waypoints;
    public final KeyMapping menu;

    public KeyBindings(KeyMapping map, KeyMapping waypoint, KeyMapping temporary, KeyMapping minimap,
            KeyMapping zoomIn, KeyMapping zoomOut, KeyMapping view, KeyMapping cave, KeyMapping waypoints,
            KeyMapping menu) {
        this.map = map;
        this.waypoint = waypoint;
        this.temporary = temporary;
        this.minimap = minimap;
        this.zoomIn = zoomIn;
        this.zoomOut = zoomOut;
        this.view = view;
        this.cave = cave;
        this.waypoints = waypoints;
        this.menu = menu;
    }

    public List<KeyMapping> all() {
        return List.of(map, waypoint, temporary, minimap, zoomIn, zoomOut, view, cave, waypoints, menu);
    }
}
