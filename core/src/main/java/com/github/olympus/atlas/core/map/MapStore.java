package com.github.olympus.atlas.core.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MapStore implements TileSource {
    private static final Logger LOGGER = LoggerFactory.getLogger("atlas");
    private static final long LIVE_NANOS = 30_000_000_000L;
    private static final long RECENT_NANOS = 2_000_000_000L;
    private static final int COARSE = 3;

    public static final class Shared {
        public final Names blocks = new Names(65535);
        public final Names biomes = new Names(65535);
        public final Palette palette = new Palette();
        public final BiomeColors biomeColors = new BiomeColors();
        public final AtomicLong used = new AtomicLong();
        public final ExecutorService work;
        public final ExecutorService io;
        public volatile long limit = 256L << 20;
        public volatile Style style = new Style(true, true, 60, true, true);

        public Shared(ExecutorService work, ExecutorService io) {
            this.work = work;
            this.io = io;
        }

        int styleKey() {
            return style.hashCode() * 31 + palette.version();
        }
    }

    private static final class Entry {
        final int rx;
        final int rz;
        final AtomicBoolean loading = new AtomicBoolean();
        Region raw;
        volatile Tile tile;
        volatile long usedAt;
        volatile long touchedAt;
        volatile int want = Tile.LEVELS;
        volatile boolean stale;
        volatile boolean missing;
        boolean dirty;
        boolean evicted;
        boolean apronKnown;
        long bytes;

        Entry(int rx, int rz) {
            this.rx = rx;
            this.rz = rz;
        }
    }

    private final Path dir;
    private final Shared shared;
    private final ConcurrentHashMap<Long, Entry> entries = new ConcurrentHashMap<>();
    private final Set<Long> onDisk = ConcurrentHashMap.newKeySet();
    private final AtomicLong revision = new AtomicLong();
    private volatile boolean closed;
    private volatile long lastSave = System.nanoTime();
    private volatile long usedAt = System.nanoTime();

    public MapStore(Path dir, Shared shared) {
        this.dir = dir;
        this.shared = shared;
        submit(shared.io, this::list);
    }

    private void list() {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(file -> {
                String[] parts = file.getFileName().toString().split("\\.");
                if (parts.length == 4 && parts[0].equals("r") && parts[3].equals("atl")) {
                    try {
                        onDisk.add(Region.key(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                    } catch (NumberFormatException ignored) {
                        return;
                    }
                }
            });
        } catch (IOException exception) {
            LOGGER.warn("Could not list {}: {}", dir, exception.getMessage());
        }
    }

    private static void submit(ExecutorService executor, Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ignored) {
            return;
        }
    }

    public long idleNanos() {
        return System.nanoTime() - usedAt;
    }

    public List<long[]> regions() {
        List<long[]> list = new ArrayList<>();
        Set<Long> seen = ConcurrentHashMap.newKeySet();
        seen.addAll(onDisk);
        seen.addAll(entries.keySet());
        for (long key : seen) {
            list.add(new long[] {key >> 32, (int) key});
        }
        return list;
    }

    @Override
    public Tile tile(int rx, int rz, int level) {
        long key = Region.key(rx, rz);
        Entry entry = entries.get(key);
        long now = System.nanoTime();
        usedAt = now;
        if (entry == null) {
            if (closed || !onDisk.contains(key)) {
                return null;
            }
            entry = entries.computeIfAbsent(key, ignored -> new Entry(rx, rz));
        }
        entry.usedAt = now;
        if (level < entry.want) {
            entry.want = level;
        }
        Tile tile = entry.tile;
        if (tile == null && entry.missing) {
            return null;
        }
        if (tile == null || tile.minLevel > level || entry.stale || tile.style != shared.styleKey()) {
            request(entry);
        }
        return tile;
    }

    public long stamp(int rx, int rz, int span) {
        long now = System.nanoTime();
        int style = shared.styleKey();
        long stamp = 0;
        usedAt = now;
        for (int z = rz; z < rz + span; z++) {
            for (int x = rx; x < rx + span; x++) {
                Entry entry = entries.get(Region.key(x, z));
                Tile tile = entry == null ? null : entry.tile;
                if (tile == null) {
                    stamp *= 31;
                    continue;
                }
                entry.usedAt = now;
                if (entry.stale || tile.style != style) {
                    request(entry);
                }
                stamp = stamp * 31 + tile.revision;
            }
        }
        return stamp;
    }

    public Tile peek(int rx, int rz) {
        Entry entry = entries.get(Region.key(rx, rz));
        return entry == null ? null : entry.tile;
    }

    public int height(int x, int z) {
        Tile tile = peek(x >> Region.BITS, z >> Region.BITS);
        return tile == null ? Region.NONE : tile.height(x, z);
    }

    private void request(Entry entry) {
        if (closed || !entry.loading.compareAndSet(false, true)) {
            return;
        }
        submit(shared.io, () -> {
            try {
                synchronized (entry) {
                    if (entry.raw == null && !entry.missing) {
                        entry.raw = read(entry.rx, entry.rz);
                        entry.missing = entry.raw == null;
                    }
                }
            } finally {
                submit(shared.work, () -> {
                    try {
                        synchronized (entry) {
                            if (entry.raw != null) {
                                bakeAll(entry);
                                relax(entry);
                            }
                        }
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Could not draw region {} {}", entry.rx, entry.rz, exception);
                    } finally {
                        entry.loading.set(false);
                    }
                });
            }
        });
    }

    private Region read(int rx, int rz) {
        Path file = file(rx, rz);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return RegionCodec.decode(Files.readAllBytes(file), rx, rz, shared.blocks, shared.biomes);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not read {}: {}", file, exception.getMessage());
            return null;
        }
    }

    private Path file(int rx, int rz) {
        return dir.resolve("r." + rx + "." + rz + ".atl");
    }

    private void bakeAll(Entry entry) {
        int key = shared.styleKey();
        Tile tile = new Tile(entry.rx, entry.rz);
        if (Baker.bake(entry.raw, tile, 0, 0, Region.SIZE, Region.SIZE, shared.style, shared.palette,
                shared.biomeColors, peek(entry.rx, entry.rz - 1), peek(entry.rx - 1, entry.rz))) {
            entry.dirty = true;
        }
        Baker.mip(tile, 0, 0, Region.SIZE, Region.SIZE);
        tile.style = key;
        tile.revision = revision.incrementAndGet();
        entry.tile = tile;
        entry.stale = false;
        entry.apronKnown = !Baker.missingApron(entry.raw);
        account(entry);
        flag(entry.rx, entry.rz + 1);
        flag(entry.rx + 1, entry.rz);
    }

    private void flag(int rx, int rz) {
        Entry neighbor = entries.get(Region.key(rx, rz));
        if (neighbor == null || neighbor.tile == null) {
            return;
        }
        if (!neighbor.apronKnown) {
            neighbor.stale = true;
        }
    }

    private void relax(Entry entry) {
        long now = System.nanoTime();
        boolean live = now - entry.touchedAt < LIVE_NANOS;
        Tile tile = entry.tile;
        if (!live && tile != null && entry.want >= COARSE && shared.used.get() > shared.limit / 2) {
            tile.trim(entry.want);
            entry.want = Tile.LEVELS;
        }
        if (!live && !entry.dirty) {
            entry.raw = null;
            entry.missing = false;
        }
        account(entry);
    }

    private void account(Entry entry) {
        Tile tile = entry.tile;
        long bytes = (entry.raw == null ? 0 : Region.BYTES) + (tile == null ? 0 : tile.bytes());
        shared.used.addAndGet(bytes - entry.bytes);
        entry.bytes = bytes;
    }

    public void apply(ChunkData chunk) {
        if (closed) {
            return;
        }
        int rx = chunk.cx >> 5;
        int rz = chunk.cz >> 5;
        long key = Region.key(rx, rz);
        int baseX = (chunk.cx << 4) & Region.MASK;
        int baseZ = (chunk.cz << 4) & Region.MASK;
        long now = System.nanoTime();
        usedAt = now;
        for (int attempt = 0; attempt < 4; attempt++) {
            Entry entry = entries.computeIfAbsent(key, ignored -> new Entry(rx, rz));
            if (write(entry, chunk, key, baseX, baseZ, now)) {
                break;
            }
        }
        if (baseZ == Region.SIZE - 16) {
            apron(chunk, rx, rz + 1, baseX, true);
        }
        if (baseX == Region.SIZE - 16) {
            apron(chunk, rx + 1, rz, baseZ, false);
        }
    }

    private boolean write(Entry entry, ChunkData chunk, long key, int baseX, int baseZ, long now) {
        int rx = entry.rx;
        int rz = entry.rz;
        synchronized (entry) {
            if (entry.evicted) {
                return false;
            }
            if (entry.raw == null) {
                entry.raw = onDisk.contains(key) ? read(rx, rz) : null;
                if (entry.raw == null) {
                    entry.raw = new Region(rx, rz);
                }
                entry.missing = false;
            }
            boolean changed = entry.raw.apply(chunk);
            entry.touchedAt = now;
            entry.usedAt = now;
            entry.dirty |= changed;
            Tile tile = entry.tile;
            if (tile == null || tile.minLevel > 0 || entry.stale || tile.style != shared.styleKey()) {
                bakeAll(entry);
            } else if (changed) {
                rebake(entry, baseX - 1, baseZ - 1, baseX + 17, baseZ + 17);
            }
            account(entry);
        }
        return true;
    }

    private void rebake(Entry entry, int x0, int z0, int x1, int z1) {
        x0 = Math.max(0, x0);
        z0 = Math.max(0, z0);
        x1 = Math.min(Region.SIZE, x1);
        z1 = Math.min(Region.SIZE, z1);
        Tile tile = entry.tile;
        if (Baker.bake(entry.raw, tile, x0, z0, x1, z1, shared.style, shared.palette, shared.biomeColors,
                peek(entry.rx, entry.rz - 1), peek(entry.rx - 1, entry.rz))) {
            entry.dirty = true;
        }
        Baker.mip(tile, x0, z0, x1, z1);
        tile.revision = revision.incrementAndGet();
    }

    private void apron(ChunkData chunk, int rx, int rz, int base, boolean south) {
        Entry entry = entries.get(Region.key(rx, rz));
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            Region raw = entry.raw;
            if (raw == null) {
                return;
            }
            boolean changed = false;
            for (int i = 0; i < 16; i++) {
                short value = south ? chunk.height[(15 << 4) | i] : chunk.height[(i << 4) | 15];
                short[] target = south ? raw.north : raw.west;
                if (target[base + i] != value) {
                    target[base + i] = value;
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
            entry.dirty = true;
            Tile tile = entry.tile;
            if (tile != null && tile.minLevel == 0) {
                if (south) {
                    rebake(entry, base, 0, base + 16, 1);
                } else {
                    rebake(entry, 0, base, 1, base + 16);
                }
            }
        }
    }

    public void maintain(long saveIntervalNanos) {
        long now = System.nanoTime();
        if (now - lastSave >= saveIntervalNanos) {
            lastSave = now;
            for (Entry entry : entries.values()) {
                save(entry);
            }
        }
        for (Entry entry : entries.values()) {
            if (now - entry.touchedAt > LIVE_NANOS && !entry.loading.get()) {
                synchronized (entry) {
                    if (entry.raw != null && !entry.dirty && entry.tile != null) {
                        entry.raw = null;
                        account(entry);
                    }
                }
            }
        }
        if (shared.used.get() <= shared.limit) {
            return;
        }
        List<Entry> oldest = new ArrayList<>(entries.values());
        oldest.sort(Comparator.comparingLong(entry -> entry.usedAt));
        long target = shared.limit * 4 / 5;
        for (Entry entry : oldest) {
            if (shared.used.get() <= target) {
                break;
            }
            if (now - entry.usedAt < RECENT_NANOS || entry.loading.get()) {
                continue;
            }
            evict(entry);
        }
    }

    private void evict(Entry entry) {
        save(entry);
        synchronized (entry) {
            if (entry.dirty) {
                return;
            }
            entries.remove(Region.key(entry.rx, entry.rz), entry);
            entry.evicted = true;
            entry.raw = null;
            entry.tile = null;
            account(entry);
        }
    }

    private void save(Entry entry) {
        byte[] data;
        synchronized (entry) {
            if (!entry.dirty || entry.raw == null) {
                return;
            }
            try {
                data = RegionCodec.encode(entry.raw, shared.blocks, shared.biomes);
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not pack region {} {}: {}", entry.rx, entry.rz, exception.getMessage());
                return;
            }
            entry.dirty = false;
        }
        Path file = file(entry.rx, entry.rz);
        try {
            Files.createDirectories(dir);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, data);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            onDisk.add(Region.key(entry.rx, entry.rz));
        } catch (IOException exception) {
            LOGGER.warn("Could not write {}: {}", file, exception.getMessage());
            synchronized (entry) {
                entry.dirty = true;
            }
        }
    }

    public void flush() {
        for (Entry entry : entries.values()) {
            save(entry);
        }
    }

    public void close() {
        closed = true;
        flush();
        for (Entry entry : entries.values()) {
            synchronized (entry) {
                entry.raw = null;
                entry.tile = null;
                account(entry);
            }
        }
        entries.clear();
    }

    public void restyle() {
        for (Entry entry : entries.values()) {
            entry.stale = true;
        }
    }
}
