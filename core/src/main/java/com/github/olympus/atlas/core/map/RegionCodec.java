package com.github.olympus.atlas.core.map;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class RegionCodec {
    private static final int MAGIC = 0x41544C53;
    private static final int VERSION = 3;

    private RegionCodec() {
    }

    public static byte[] encode(Region region, Names blocks, Names biomes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 17);
        DataOutputStream head = new DataOutputStream(bytes);
        head.writeInt(MAGIC);
        head.writeByte(VERSION);
        head.flush();
        Deflater deflater = new Deflater(5);
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes, deflater, 1 << 15))) {
            Local blockPalette = new Local(blocks);
            short[] block = blockPalette.localize(region.block);
            short[] under = blockPalette.localize(region.under);
            short[] ground = blockPalette.localize(region.ground);
            short[] body = blockPalette.localize(region.body);
            Local biomePalette = new Local(biomes);
            short[] biome = biomePalette.localize(region.biome);
            blockPalette.write(out);
            biomePalette.write(out);
            short[] delta = new short[Region.AREA];
            short previous = 0;
            for (int i = 0; i < Region.AREA; i++) {
                delta[i] = (short) (region.height[i] - previous);
                previous = region.height[i];
            }
            planes(out, delta);
            planes(out, block);
            planes(out, under);
            planes(out, biome);
            out.write(region.depth);
            out.write(region.light);
            planes(out, region.north);
            planes(out, region.west);
            out.write(region.thick);
            out.write(region.gap);
            planes(out, ground);
            planes(out, body);
        } finally {
            deflater.end();
        }
        return bytes.toByteArray();
    }

    public static Region decode(byte[] data, int rx, int rz, Names blocks, Names biomes) throws IOException {
        DataInputStream head = new DataInputStream(new ByteArrayInputStream(data));
        if (head.readInt() != MAGIC) {
            throw new IOException("not an atlas region");
        }
        int version = head.readByte();
        if (version < 1 || version > VERSION) {
            throw new IOException("unknown region version " + version);
        }
        Region region = new Region(rx, rz);
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(
                new ByteArrayInputStream(data, 5, data.length - 5)))) {
            int[] blockIds = palette(in, blocks);
            int[] biomeIds = palette(in, biomes);
            planes(in, region.height);
            short previous = 0;
            for (int i = 0; i < Region.AREA; i++) {
                previous = (short) (region.height[i] + previous);
                region.height[i] = previous;
            }
            planes(in, region.block);
            planes(in, region.under);
            planes(in, region.biome);
            globalize(region.block, blockIds);
            globalize(region.under, blockIds);
            globalize(region.biome, biomeIds);
            in.readFully(region.depth);
            in.readFully(region.light);
            planes(in, region.north);
            planes(in, region.west);
            if (version >= 2) {
                in.readFully(region.thick);
                in.readFully(region.gap);
                planes(in, region.ground);
                globalize(region.ground, blockIds);
            }
            if (version >= 3) {
                planes(in, region.body);
                globalize(region.body, blockIds);
            }
        }
        return region;
    }

    private static void planes(DataOutputStream out, short[] values) throws IOException {
        byte[] plane = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            plane[i] = (byte) values[i];
        }
        out.write(plane);
        for (int i = 0; i < values.length; i++) {
            plane[i] = (byte) (values[i] >> 8);
        }
        out.write(plane);
    }

    private static void planes(DataInputStream in, short[] values) throws IOException {
        byte[] low = new byte[values.length];
        byte[] high = new byte[values.length];
        in.readFully(low);
        in.readFully(high);
        for (int i = 0; i < values.length; i++) {
            values[i] = (short) ((low[i] & 0xFF) | (high[i] << 8));
        }
    }

    private static int[] palette(DataInputStream in, Names names) throws IOException {
        int count = in.readUnsignedShort();
        int[] ids = new int[count + 1];
        for (int i = 1; i <= count; i++) {
            ids[i] = names.id(in.readUTF());
        }
        return ids;
    }

    private static void globalize(short[] values, int[] ids) {
        for (int i = 0; i < values.length; i++) {
            int local = values[i] & 0xFFFF;
            values[i] = (short) (local < ids.length ? ids[local] : 0);
        }
    }

    private static final class Local {
        private final Names names;
        private final List<String> used = new ArrayList<>();
        private int[] local;

        private Local(Names names) {
            this.names = names;
            this.local = new int[names.size()];
        }

        private short[] localize(short[] values) {
            short[] mapped = new short[values.length];
            for (int i = 0; i < values.length; i++) {
                int id = values[i] & 0xFFFF;
                if (id == 0) {
                    continue;
                }
                if (id >= local.length) {
                    local = Arrays.copyOf(local, id + 64);
                }
                int index = local[id];
                if (index == 0) {
                    used.add(names.name(id));
                    index = used.size();
                    local[id] = index;
                }
                mapped[i] = (short) index;
            }
            return mapped;
        }

        private void write(DataOutputStream out) throws IOException {
            out.writeShort(used.size());
            for (String name : used) {
                out.writeUTF(name);
            }
        }
    }
}
