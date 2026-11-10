package com.github.olympus.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.olympus.atlas.core.map.Baker;
import com.github.olympus.atlas.core.map.BiomeColors;
import com.github.olympus.atlas.core.map.Palette;
import com.github.olympus.atlas.core.map.Region;
import com.github.olympus.atlas.core.map.Style;
import com.github.olympus.atlas.core.map.Tile;
import com.github.olympus.atlas.core.map.TileSource;
import com.github.olympus.atlas.core.render.BeamShape;
import com.github.olympus.atlas.core.render.Camera;
import com.github.olympus.atlas.core.render.MapPainter;
import com.github.olympus.atlas.core.render.TerrainRenderer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderTest {
    private static Tile flat(int height, int tower) {
        Region region = new Region(0, 0);
        Palette palette = new Palette();
        palette.define(1, 0x808080, false);
        palette.define(2, 0xFF0000, false);
        for (int i = 0; i < Region.AREA; i++) {
            region.height[i] = (short) height;
            region.block[i] = 1;
        }
        int roof = (200 << Region.BITS) | 200;
        region.height[roof] = 80;
        region.thick[roof] = 2;
        region.gap[roof] = 14;
        region.ground[roof] = 2;
        int at = (100 << Region.BITS) | 100;
        region.height[at] = (short) tower;
        region.block[at] = 2;
        Tile tile = new Tile(0, 0);
        Baker.bake(region, tile, 0, 0, Region.SIZE, Region.SIZE, new Style(false, false, 0, false, false), palette,
                new BiomeColors(), null, null);
        Baker.mip(tile, 0, 0, Region.SIZE, Region.SIZE);
        return tile;
    }

    private static TileSource only(Tile tile) {
        return (rx, rz, level) -> rx == 0 && rz == 0 ? tile : null;
    }

    @Test
    void mipsKeepTheHighestPoint() {
        Tile tile = flat(64, 200);
        assertEquals(200, tile.top[Tile.LEVELS - 1][0]);
        assertEquals(64, tile.top[3][0]);
        assertEquals(0xFF808080, tile.color[2][0]);
    }

    @Test
    void paintsBlocksWhereTheyAre() {
        Tile tile = flat(64, 200);
        int[] out = new int[9];
        MapPainter.paint(only(tile), new MapPainter.View(100.5, 100.5, 1.0, 0.0, 0xFF000000, false), out, 3, 3, 0, 3);
        assertEquals(0xFFFF0000, out[4]);
        assertEquals(0xFF808080, out[0]);
        MapPainter.paint(only(tile), new MapPainter.View(-50.0, -50.0, 1.0, 0.0, 0xFF123456, false), out, 3, 3, 0, 3);
        assertEquals(0xFF123456, out[4]);
    }

    @Test
    void picksCoarserLevelsWhenZoomedOut() {
        assertEquals(0, MapPainter.level(2.0));
        assertEquals(0, MapPainter.level(1.0));
        assertEquals(1, MapPainter.level(0.5));
        assertEquals(3, MapPainter.level(0.125));
    }

    @Test
    void raysHitTheGroundAndTheTower() {
        Tile tile = flat(64, 200);
        TerrainRenderer renderer = new TerrainRenderer(only(tile));
        assertTrue(renderer.trace(50.5, 100.0, 50.5, 0.0, -1.0, 0.0, 1000.0, 0.001, 400));
        assertEquals(35.0, renderer.hitDistance(), 0.01);
        assertTrue(renderer.trace(50.5, 150.0, 100.5, 1.0, 0.0, 0.0, 1000.0, 0.001, 400));
        assertEquals(49.5, renderer.hitDistance(), 0.05);
        assertFalse(renderer.trace(50.5, 100.0, 50.5, 0.0, 1.0, 0.0, 1000.0, 0.001, 400));
        assertFalse(renderer.trace(-900.0, 100.0, -900.0, -1.0, -0.01, 0.0, 500.0, 0.001, 400));
    }

    @Test
    void raysPassUnderAnOverhang() {
        Tile tile = flat(64, 200);
        TerrainRenderer renderer = new TerrainRenderer(only(tile));
        assertTrue(renderer.trace(190.5, 80.5, 200.5, 1.0, 0.0, 0.0, 1000.0, 0.001, 400));
        assertEquals(9.5, renderer.hitDistance(), 0.05);
        assertFalse(renderer.trace(190.5, 72.0, 200.5, 1.0, 0.0, 0.0, 300.0, 0.001, 400));
        assertTrue(renderer.trace(200.5, 100.0, 200.5, 0.0, -1.0, 0.0, 1000.0, 0.001, 400));
        assertEquals(19.0, renderer.hitDistance(), 0.01);
        assertTrue(renderer.trace(200.5, 72.0, 200.5, 0.0, -1.0, 0.0, 1000.0, 0.001, 400));
        assertEquals(7.0, renderer.hitDistance(), 0.01);
        assertTrue(renderer.trace(200.5, 72.0, 200.5, 0.0, 1.0, 0.0, 1000.0, 0.001, 400));
        assertEquals(7.0, renderer.hitDistance(), 0.01);
    }

    @Test
    void appliesTheViewSway() {
        Camera camera = new Camera(0.0, 70.0, 0.0, 0.0, 0.0, 70.0, 1.0);
        camera.post = new double[] {1, 0, 0, 0.5, 0, 1, 0, 0, 0, 0, 1, 0};
        double[] out = new double[3];
        assertTrue(camera.project(0.0, 70.0, 10.0, 100.0, 100.0, out));
        assertTrue(out[0] > 50.0);
        assertEquals(50.0, out[1], 0.001);
    }

    @Test
    void projectsWhatIsAheadToTheCenter() {
        Camera camera = new Camera(0.0, 70.0, 0.0, 0.0, 0.0, 70.0, 16.0 / 9.0);
        double[] out = new double[3];
        assertTrue(camera.project(0.0, 70.0, 10.0, 1920.0, 1080.0, out));
        assertEquals(960.0, out[0], 0.001);
        assertEquals(540.0, out[1], 0.001);
        assertFalse(camera.project(0.0, 70.0, -10.0, 1920.0, 1080.0, out));
        assertTrue(camera.project(-2.0, 71.0, 10.0, 1920.0, 1080.0, out));
        assertTrue(out[0] > 960.0);
        assertTrue(out[1] < 540.0);
    }

    @Test
    void beamsAreFourSidedColumns() {
        List<float[]> core = new ArrayList<>();
        BeamShape.core((x, y, z, u, v) -> core.add(new float[] {x, y, z, u, v}), 7.5f);
        assertEquals(16, core.size());
        for (float[] vertex : core) {
            assertEquals(BeamShape.CORE, Math.hypot(vertex[0], vertex[2]), 1.0e-4);
            assertTrue(vertex[1] == 0f || vertex[1] == BeamShape.HEIGHT);
        }
        List<float[]> glow = new ArrayList<>();
        BeamShape.glow((x, y, z, u, v) -> glow.add(new float[] {x, y, z, u, v}), 7.5f);
        assertEquals(16, glow.size());
        for (float[] vertex : glow) {
            assertEquals(BeamShape.GLOW, Math.abs(vertex[0]), 1.0e-6);
            assertEquals(BeamShape.GLOW, Math.abs(vertex[2]), 1.0e-6);
        }
        assertEquals(BeamShape.scroll(0f), BeamShape.scroll(BeamShape.PERIOD), 1.0e-4);
    }

    @Test
    void beamsFadeWhenNearAndAtTheDistanceLimit() {
        assertEquals(1f, BeamShape.fade(200.0, 200.0, 0), 1.0e-6);
        assertEquals(0.12f, BeamShape.fade(0.5, 3.0, 0), 1.0e-6);
        assertTrue(BeamShape.fade(7.0, 7.0, 0) < 1f);
        assertTrue(BeamShape.fade(7.0, 7.0, 0) > 0.12f);
        assertEquals(0f, BeamShape.fade(600.0, 600.0, 500), 1.0e-6);
        assertEquals(0.5f, BeamShape.fade(450.0, 450.0, 500), 1.0e-4);
    }
}
