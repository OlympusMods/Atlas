package com.github.olympus.atlas.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.olympus.atlas.core.map.ChunkData;
import com.github.olympus.atlas.core.map.Names;
import com.github.olympus.atlas.core.map.Region;
import com.github.olympus.atlas.core.map.RegionCodec;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class RegionCodecTest {
    private static Region sample(Names blocks, Names biomes) {
        Region region = new Region(-2, 3);
        int stone = blocks.id("minecraft:stone");
        int water = blocks.id("minecraft:water");
        int plains = biomes.id("minecraft:plains");
        for (int i = 0; i < Region.AREA; i += 7) {
            region.height[i] = (short) (i % 300 - 60);
            region.block[i] = (short) (i % 3 == 0 ? water : stone);
            region.under[i] = (short) (i % 3 == 0 ? stone : 0);
            region.depth[i] = (byte) (i % 3 == 0 ? 5 : 0);
            region.biome[i] = (short) plains;
            region.light[i] = (byte) (i % 16);
            region.thick[i] = (byte) (i % 5);
            region.gap[i] = (byte) (i % 9);
            region.ground[i] = (short) stone;
        }
        region.north[10] = 70;
        region.west[500] = -12;
        return region;
    }

    @Test
    void survivesARoundTrip() throws IOException {
        Names blocks = new Names(65535);
        Names biomes = new Names(65535);
        Region region = sample(blocks, biomes);
        byte[] packed = RegionCodec.encode(region, blocks, biomes);
        Region read = RegionCodec.decode(packed, -2, 3, blocks, biomes);
        assertArrayEquals(region.height, read.height);
        assertArrayEquals(region.block, read.block);
        assertArrayEquals(region.under, read.under);
        assertArrayEquals(region.depth, read.depth);
        assertArrayEquals(region.biome, read.biome);
        assertArrayEquals(region.light, read.light);
        assertArrayEquals(region.thick, read.thick);
        assertArrayEquals(region.gap, read.gap);
        assertArrayEquals(region.ground, read.ground);
        assertArrayEquals(region.north, read.north);
        assertArrayEquals(region.west, read.west);
    }

    @Test
    void remapsNamesIntoAnotherSession() throws IOException {
        Names blocks = new Names(65535);
        Names biomes = new Names(65535);
        Region region = sample(blocks, biomes);
        byte[] packed = RegionCodec.encode(region, blocks, biomes);
        Names otherBlocks = new Names(65535);
        otherBlocks.id("minecraft:dirt");
        otherBlocks.id("minecraft:water");
        Names otherBiomes = new Names(65535);
        Region read = RegionCodec.decode(packed, -2, 3, otherBlocks, otherBiomes);
        assertEquals("minecraft:water", otherBlocks.name(read.block[0] & 0xFFFF));
        assertEquals("minecraft:stone", otherBlocks.name(read.block[7] & 0xFFFF));
        assertEquals("minecraft:plains", otherBiomes.name(read.biome[7] & 0xFFFF));
    }

    @Test
    void staysCompact() throws IOException {
        Names blocks = new Names(65535);
        Names biomes = new Names(65535);
        byte[] packed = RegionCodec.encode(sample(blocks, biomes), blocks, biomes);
        assertTrue(packed.length < Region.BYTES / 8, "packed to " + packed.length + " bytes");
    }

    @Test
    void appliesChunksOnlyWhenTheyChange() {
        Region region = new Region(0, 0);
        ChunkData chunk = new ChunkData(1, 2);
        chunk.height[5] = 64;
        chunk.block[5] = 3;
        assertTrue(region.apply(chunk));
        assertEquals(64, region.height[(32 << Region.BITS) | 21]);
        assertEquals(false, region.apply(chunk));
    }
}
