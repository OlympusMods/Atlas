package com.github.olympus.atlas.core.fair;

public final class FairPlay {
    private static final String XAERO_FAIR = "§f§a§i§r§x§a§e§r§o";
    private static final String XAERO_NETHER = "§x§a§e§r§o§w§m§n§e§t§h§e§r§i§s§f§a§i§r";
    private static final String XAERO_NO_MINIMAP = "§n§o§m§i§n§i§m§a§p";
    private static final String XAERO_RESET = "§r§e§s§e§t§x§a§e§r§o";
    private static final String VOXEL_PREFIX = "§3 §6 §3 §6 §3 §6 ";
    private static final String VOXEL_NO_RADAR = VOXEL_PREFIX + "§e";
    private static final String VOXEL_NO_CAVES = VOXEL_PREFIX + "§d";
    private static final String VOXEL_RADAR = VOXEL_PREFIX + "§0";
    private static final String VOXEL_CAVES = VOXEL_PREFIX + "§f";

    private volatile boolean minimap = true;
    private volatile boolean caves = true;
    private volatile boolean netherCaves = true;
    private volatile boolean radar = true;

    public boolean read(String message) {
        if (message == null || message.indexOf('§') < 0) {
            return false;
        }
        boolean changed = false;
        if (message.contains(XAERO_RESET)) {
            reset();
            changed = true;
        }
        if (message.contains(XAERO_FAIR)) {
            caves = false;
            netherCaves = false;
            radar = false;
            changed = true;
        }
        if (message.contains(XAERO_NETHER)) {
            netherCaves = true;
            changed = true;
        }
        if (message.contains(XAERO_NO_MINIMAP)) {
            minimap = false;
            changed = true;
        }
        if (message.contains(VOXEL_NO_RADAR)) {
            radar = false;
            changed = true;
        }
        if (message.contains(VOXEL_NO_CAVES)) {
            caves = false;
            netherCaves = false;
            changed = true;
        }
        if (message.contains(VOXEL_RADAR)) {
            radar = true;
            changed = true;
        }
        if (message.contains(VOXEL_CAVES)) {
            caves = true;
            netherCaves = true;
            changed = true;
        }
        return changed;
    }

    public void reset() {
        minimap = true;
        caves = true;
        netherCaves = true;
        radar = true;
    }

    public boolean minimap() {
        return minimap;
    }

    public boolean caves(boolean ceiling) {
        return ceiling ? netherCaves : caves;
    }

    public boolean radar() {
        return radar;
    }

    public boolean limited() {
        return !minimap || !caves || !netherCaves || !radar;
    }
}
