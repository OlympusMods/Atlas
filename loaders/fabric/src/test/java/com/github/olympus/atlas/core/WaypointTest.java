package com.github.olympus.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.olympus.atlas.core.fair.FairPlay;
import com.github.olympus.atlas.core.waypoint.Importers;
import com.github.olympus.atlas.core.waypoint.Waypoint;
import com.github.olympus.atlas.core.waypoint.WaypointStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WaypointTest {
    private static Waypoint at(String name, int x, Waypoint.Kind kind, long created) {
        Waypoint waypoint = new Waypoint();
        waypoint.name = name;
        waypoint.x = x;
        waypoint.kind = kind;
        waypoint.created = created;
        return waypoint;
    }

    @Test
    void savesAndLoads(@TempDir Path dir) {
        Path file = dir.resolve("waypoints.json");
        WaypointStore store = new WaypointStore(file);
        store.add(at("Home", 10, Waypoint.Kind.NORMAL, 1));
        store.hideGroup("", true);
        assertTrue(store.saveIfDirty());
        WaypointStore read = new WaypointStore(file);
        assertEquals(1, read.all().size());
        assertEquals("Home", read.all().get(0).name);
        assertFalse(read.shown(read.all().get(0)));
    }

    @Test
    void keepsOnlyTheNewestDeaths() {
        WaypointStore store = new WaypointStore(null);
        for (int i = 0; i < 5; i++) {
            store.add(at("Death " + i, i, Waypoint.Kind.DEATH, 1000 + i));
        }
        store.add(at("Home", 99, Waypoint.Kind.NORMAL, 1));
        assertEquals(3, store.trimDeaths(2, 0, 2000));
        assertEquals(3, store.all().size());
        assertEquals(1, store.trimDeaths(2, 500, 1504));
    }

    @Test
    void dropsTemporaryOnesOnArrivalOrExpiry() {
        WaypointStore store = new WaypointStore(null);
        store.add(at("Near", 0, Waypoint.Kind.TEMPORARY, 1000));
        store.add(at("Far", 500, Waypoint.Kind.TEMPORARY, 1000));
        assertEquals(1, store.trimTemporary(0, 1001, "minecraft:overworld", 0.5, 0, 0.5, true));
        assertEquals(1, store.trimTemporary(100, 2000, "minecraft:overworld", 0.5, 0, 0.5, true));
    }

    @Test
    void mergeSkipsDuplicates() {
        WaypointStore store = new WaypointStore(null);
        store.add(at("Home", 10, Waypoint.Kind.NORMAL, 1));
        assertEquals(1, store.merge(List.of(at("Home", 10, Waypoint.Kind.NORMAL, 5), at("Mine", 20,
                Waypoint.Kind.NORMAL, 5))));
    }

    @Test
    void readsXaeroLines() {
        List<Waypoint> read = Importers.xaero(List.of("#comment", "waypoint:Base:B:120:~:-340:10:false:0:gui.xaero_default:false:0:0:false",
                "waypoint:Last death:D:5:60:6:4:true:1:gui.xaero_default:false:0:0:false"),
                Importers.xaeroDimension("dim%-1"));
        assertEquals(2, read.size());
        assertEquals("Base", read.get(0).name);
        assertEquals(120, read.get(0).x);
        assertEquals(-340, read.get(0).z);
        assertEquals(0x55FF55, read.get(0).color);
        assertEquals("minecraft:the_nether", read.get(0).dimension);
        assertEquals(Waypoint.Kind.DEATH, read.get(1).kind);
        assertFalse(read.get(1).visible);
        assertEquals("minecraft:overworld", Importers.xaeroDimension("dim%0"));
        assertEquals("mod:moon", Importers.xaeroDimension("dim%mod$moon"));
    }

    @Test
    void readsJourneyMapAndVoxelMap() {
        Waypoint journey = Importers.journey("{\"name\":\"Farm\",\"x\":1,\"y\":70,\"z\":-2,\"r\":255,\"g\":0,\"b\":16,"
                + "\"enable\":true,\"type\":\"Normal\",\"dimensions\":[\"minecraft:the_end\"]}");
        assertEquals("Farm", journey.name);
        assertEquals(0xFF0010, journey.color);
        assertEquals("minecraft:the_end", journey.dimension);
        List<Waypoint> voxel = Importers.voxel(List.of("subworlds:", "name:Hut,x:80,z:-16,y:64,enabled:true,red:1.0,"
                + "green:0.0,blue:0.0,suffix:,world:,dimensions:the_nether#"));
        assertEquals(1, voxel.size());
        assertEquals(10, voxel.get(0).x);
        assertEquals(-2, voxel.get(0).z);
        assertEquals(0xFF0000, voxel.get(0).color);
    }

    @Test
    void readsAnAtlasExport(@TempDir Path dir) throws IOException {
        WaypointStore store = new WaypointStore(null);
        store.add(at("Home", 10, Waypoint.Kind.NORMAL, 1));
        Path file = dir.resolve("atlas").resolve("import").resolve("mine.json");
        assertTrue(WaypointStore.write(file, store.document()));
        List<Importers.Source> sources = Importers.find(dir);
        assertEquals(1, sources.size());
        assertEquals(1, Importers.load(sources.get(0)).size());
        assertTrue(Files.exists(file));
    }

    @Test
    void followsServerRules() {
        FairPlay fair = new FairPlay();
        assertFalse(fair.read("Welcome to the server"));
        assertTrue(fair.read("§f§a§i§r§x§a§e§r§o"));
        assertFalse(fair.radar());
        assertFalse(fair.caves(false));
        assertFalse(fair.caves(true));
        assertTrue(fair.minimap());
        assertTrue(fair.read("§x§a§e§r§o§w§m§n§e§t§h§e§r§i§s§f§a§i§r"));
        assertTrue(fair.caves(true));
        assertTrue(fair.read("§n§o§m§i§n§i§m§a§p"));
        assertFalse(fair.minimap());
        assertTrue(fair.read("§r§e§s§e§t§x§a§e§r§o"));
        assertFalse(fair.limited());
        assertTrue(fair.read("§3 §6 §3 §6 §3 §6 §e"));
        assertFalse(fair.radar());
        assertTrue(fair.read("§3 §6 §3 §6 §3 §6 §d"));
        assertFalse(fair.caves(false));
    }

    @Test
    void clampsTheConfig() {
        AtlasConfig config = new AtlasConfig();
        config.minimapSize = 9000;
        config.minimapZoom = -4;
        config.teleportCommand = "/tp {x} {y} {z}";
        config.colorMode = null;
        config.sanitize();
        assertEquals(260, config.minimapSize);
        assertEquals(0, config.minimapZoom);
        assertEquals("tp {x} {y} {z}", config.teleportCommand);
        assertEquals(AtlasConfig.ColorMode.TEXTURE, config.colorMode);
    }
}
