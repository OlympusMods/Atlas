package com.github.olympus.atlas.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.olympus.atlas.core.map.ChunkData;
import com.github.olympus.atlas.core.map.MapWorld;
import com.github.olympus.atlas.core.map.Names;
import com.github.olympus.atlas.core.map.Region;
import com.github.olympus.atlas.core.map.RegionCodec;
import com.github.olympus.atlas.core.map.SubWorlds;
import com.github.olympus.atlas.core.map.SubWorlds.Sight;
import com.github.olympus.atlas.core.map.SubWorlds.Terrain;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SubWorldsTest {
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private final Names biomes = new Names(65535);
    private final int plains = biomes.id("minecraft:plains");
    private final int forest = biomes.id("minecraft:forest");
    private final int desert = biomes.id("minecraft:desert");

    private static int hills(int x, int z, int salt) {
        return 64 + Math.floorMod(x * 7 + z * 13 + salt * 31 + (x * z) % 11, 40);
    }

    private List<ChunkData> chunks(int salt, int land) {
        List<ChunkData> chunks = new ArrayList<>();
        for (int cx = 3; cx < 6; cx++) {
            ChunkData chunk = new ChunkData(cx, 4);
            for (int i = 0; i < ChunkData.AREA; i++) {
                int x = (cx << 4) + (i & 15);
                chunk.height[i] = (short) hills(x, 64 + (i >> 4), salt);
                chunk.block[i] = 1;
                chunk.biome[i] = (short) (land == 0 ? 0 : land == 1 ? plains : land == 2 ? (x < 70 ? forest : plains)
                        : (x < 70 ? desert : forest));
            }
            chunks.add(chunk);
        }
        return chunks;
    }

    private List<ChunkData> chunks(int salt) {
        return chunks(salt, 0);
    }

    private void explore(Path folder, List<ChunkData> chunks) throws IOException {
        Region region = new Region(0, 0);
        for (ChunkData chunk : chunks) {
            region.apply(chunk);
        }
        Names blocks = new Names(65535);
        blocks.id("minecraft:stone");
        Path directory = MapWorld.directory(folder, OVERWORLD, MapWorld.SURFACE);
        Files.createDirectories(directory);
        Files.write(directory.resolve("r.0.0.atl"), RegionCodec.encode(region, blocks, biomes));
    }

    private static Sight sight(Long seed, int x, List<ChunkData> chunks) {
        return new Sight(seed, OVERWORLD, MapWorld.SURFACE, x, 70, 0, chunks);
    }

    private String join(SubWorlds worlds, Long seed, int x, String previous, List<ChunkData> chunks) {
        return worlds.resolve(sight(seed, x, chunks), true, previous, biomes);
    }

    @Test
    void theFirstWorldKeepsTheOldFolder(@TempDir Path root) {
        SubWorlds worlds = new SubWorlds(root);
        assertNull(worlds.bySeed(11L));
        assertEquals("", join(worlds, 11L, 0, null, chunks(1)));
        assertEquals(root, worlds.folder(""));
        assertEquals("", worlds.bySeed(11L));
    }

    @Test
    void anotherSeedGetsItsOwnFolder(@TempDir Path root) {
        SubWorlds worlds = new SubWorlds(root);
        join(worlds, 11L, 0, null, chunks(1));
        assertEquals("2", join(worlds, 22L, 5000, "", chunks(2)));
        assertEquals(root.resolve("worlds").resolve("2"), worlds.folder("2"));
        assertEquals("3", join(worlds, 33L, 9000, "2", chunks(3)));
        SubWorlds reopened = new SubWorlds(root);
        assertEquals("", reopened.bySeed(11L));
        assertEquals("2", reopened.bySeed(22L));
        assertEquals(List.of("", "2", "3"), reopened.ids());
        assertEquals("2", join(reopened, 22L, -4000, "", chunks(9)));
    }

    @Test
    void aSeedSharedByTwoWorldsStopsCounting(@TempDir Path root) throws IOException {
        SubWorlds worlds = new SubWorlds(root);
        join(worlds, 11L, 0, null, chunks(1, 2));
        explore(root, chunks(1, 2));
        assertEquals("2", join(worlds, 11L, 5000, "", chunks(2, 3)));
        assertNull(worlds.bySeed(11L));
        explore(worlds.folder("2"), chunks(2, 3));
        assertEquals("", join(worlds, 11L, 300, "2", chunks(1, 2)));
        assertEquals("2", join(worlds, 11L, 700, "", chunks(2, 3)));
    }

    @Test
    void whereYouLeftTellsWorldsApartWithoutASeed(@TempDir Path root) {
        SubWorlds worlds = new SubWorlds(root);
        assertEquals("", join(worlds, null, 0, null, List.of()));
        worlds.leave("", OVERWORLD, 3, 70, 2);
        assertEquals("", join(worlds, null, 8, "", List.of()));
        String other = worlds.assign(null, sight(null, 5000, List.of()));
        assertEquals("2", other);
        worlds.leave("2", OVERWORLD, 6400, 70, 0);
        assertEquals("", join(worlds, null, 0, "2", List.of()));
        assertEquals("2", join(worlds, null, 6405, "", List.of()));
        assertEquals("2", join(worlds, null, 5003, "", List.of()));
        assertEquals("2", join(new SubWorlds(root), null, 6390, "", List.of()));
    }

    @Test
    void scrambledSeedsAreFoundOutAndIgnored(@TempDir Path root) throws IOException {
        SubWorlds worlds = new SubWorlds(root);
        join(worlds, 11L, 0, null, chunks(1, 2));
        explore(root, chunks(1, 2));
        assertEquals("2", join(worlds, 22L, 5000, "", chunks(2, 3)));
        assertEquals("", join(worlds, 33L, 0, "2", chunks(1, 2)));
        assertNull(worlds.bySeed(11L));
        assertNull(worlds.bySeed(22L));
        assertEquals("", join(worlds, 44L, 3, "2", List.of()));
        assertEquals("2", join(worlds, 55L, 5001, "", List.of()));
        assertEquals("2", join(worlds, 66L, 90000, "", List.of()));
        assertFalse(Files.exists(root.resolve("worlds").resolve("3")));
    }

    @Test
    void aPlaceAloneDoesNotOverruleAnotherSeed(@TempDir Path root) {
        SubWorlds worlds = new SubWorlds(root);
        join(worlds, 11L, 0, null, List.of());
        assertEquals("2", join(worlds, 22L, 4, "", List.of()));
        assertEquals("", worlds.bySeed(11L));
        assertEquals("2", worlds.bySeed(22L));
    }

    @Test
    void buildingDoesNotChangeTheWorld(@TempDir Path root) throws IOException {
        SubWorlds worlds = new SubWorlds(root);
        join(worlds, null, 0, null, chunks(1, 2));
        explore(root, chunks(1, 2));
        Path directory = MapWorld.directory(root, OVERWORLD, MapWorld.SURFACE);
        assertEquals(Terrain.SAME, SubWorlds.compare(directory, chunks(1, 2), biomes));
        assertEquals(Terrain.SAME, SubWorlds.compare(directory, chunks(7, 2), biomes));
        assertEquals(Terrain.OTHER, SubWorlds.compare(directory, chunks(1, 3), biomes));
        assertEquals("", join(worlds, null, 900, "", chunks(7, 2)));
    }

    @Test
    void oneBiomeIsNotEnoughToBeSure(@TempDir Path root) throws IOException {
        explore(root, chunks(1, 1));
        Path directory = MapWorld.directory(root, OVERWORLD, MapWorld.SURFACE);
        assertEquals(Terrain.SAME, SubWorlds.compare(directory, chunks(1, 1), biomes));
        assertEquals(Terrain.DOUBT, SubWorlds.compare(directory, chunks(7, 1), biomes));
        assertEquals(Terrain.UNKNOWN, SubWorlds.compare(directory, List.of(new ChunkData(20, 20)), biomes));
        assertEquals(Terrain.UNKNOWN, SubWorlds.compare(root.resolve("nothing"), chunks(1, 1), biomes));
        SubWorlds worlds = new SubWorlds(root);
        assertEquals("2", join(worlds, 11L, 0, null, chunks(7, 1)));
    }

    @Test
    void aPortalStaysInTheSameWorld(@TempDir Path root) {
        SubWorlds worlds = new SubWorlds(root);
        join(worlds, 11L, 0, null, chunks(1));
        join(worlds, 22L, 5000, "", chunks(2));
        assertEquals("2", worlds.resolve(new Sight(99L, NETHER, 64, 600, 70, 0, chunks(5)), false, "2", biomes));
        assertEquals("2", worlds.bySeed(99L));
    }

    @Test
    void anOldMapOfOtherTerrainIsLeftAlone(@TempDir Path root) throws IOException {
        explore(root, chunks(1));
        SubWorlds worlds = new SubWorlds(root);
        assertEquals("2", join(worlds, 11L, 0, null, chunks(4)));
        assertEquals("", join(worlds, 12L, 0, "2", chunks(1)));
    }

    @Test
    void aChoiceByHandIsRemembered(@TempDir Path root) {
        SubWorlds worlds = new SubWorlds(root);
        join(worlds, 11L, 0, null, List.of());
        assertEquals("2", worlds.assign(null, sight(11L, 0, List.of())));
        assertEquals("2", worlds.bySeed(11L));
        assertEquals("2", join(worlds, 11L, 2, "", List.of()));
        assertEquals("", worlds.assign("", sight(11L, 2, List.of())));
        assertEquals("", join(new SubWorlds(root), 11L, 2, "2", List.of()));
    }
}
