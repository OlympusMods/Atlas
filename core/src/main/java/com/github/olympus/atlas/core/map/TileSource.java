package com.github.olympus.atlas.core.map;

public interface TileSource {
    Tile tile(int rx, int rz, int level);
}
