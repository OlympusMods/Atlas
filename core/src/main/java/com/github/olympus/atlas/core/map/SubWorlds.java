package com.github.olympus.atlas.core.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SubWorlds {
    public static final String ROOT = "";

    private static final Logger LOGGER = LoggerFactory.getLogger("atlas");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SEEDS = 16;
    private static final int ARRIVALS = 8;
    private static final int ENOUGH = 96;
    private static final int NEAR = 16;
    private static final double SAME_HEIGHTS = 0.6;
    private static final double OTHER_HEIGHTS = 0.3;
    private static final double SAME_BIOMES = 0.9;
    private static final double OTHER_BIOMES = 0.5;
    private static final double VARIED = 0.05;
    private static final int SEED_POINTS = 3;
    private static final int TERRAIN_POINTS = 3;
    private static final int PLACE_POINTS = 2;

    public enum Terrain {
        SAME,
        OTHER,
        DOUBT,
        UNKNOWN
    }

    public record Sight(Long seed, String dimension, int layer, int x, int y, int z, List<ChunkData> chunks) {
    }

    private static final class Spot {
        String dimension = "";
        int x;
        int y;
        int z;

        boolean near(String place, int px, int py, int pz) {
            return dimension.equals(place) && Math.abs(x - px) <= NEAR && Math.abs(y - py) <= NEAR
                    && Math.abs(z - pz) <= NEAR;
        }
    }

    private static final class Entry {
        String id = ROOT;
        List<Long> seeds = new ArrayList<>();
        Map<String, Long> seedOf = new LinkedHashMap<>();
        List<Spot> left = new ArrayList<>();
        List<Spot> arrivals = new ArrayList<>();

        boolean claimed() {
            return !seeds.isEmpty() || !left.isEmpty() || !arrivals.isEmpty();
        }

        boolean near(Sight sight) {
            for (Spot spot : left) {
                if (spot.near(sight.dimension(), sight.x(), sight.y(), sight.z())) {
                    return true;
                }
            }
            for (Spot spot : arrivals) {
                if (spot.near(sight.dimension(), sight.x(), sight.y(), sight.z())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class Document {
        int version = 1;
        String last = ROOT;
        boolean scrambled;
        List<Long> shared = new ArrayList<>();
        List<Entry> worlds = new ArrayList<>();
    }

    private final Path root;
    private final Path file;
    private Document document = new Document();

    public SubWorlds(Path root) {
        this.root = root;
        this.file = root.resolve("worlds.json");
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Document read = GSON.fromJson(reader, Document.class);
                if (read != null) {
                    document = read;
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Could not read {}: {}", file, exception.getMessage());
            }
        }
        clean();
    }

    private void clean() {
        if (document.worlds == null) {
            document.worlds = new ArrayList<>();
        }
        if (document.shared == null) {
            document.shared = new ArrayList<>();
        }
        document.shared.removeIf(seed -> seed == null);
        Set<String> seen = new HashSet<>();
        document.worlds.removeIf(entry -> entry == null || !named(entry.id) || !seen.add(entry.id));
        for (Entry entry : document.worlds) {
            if (entry.seeds == null) {
                entry.seeds = new ArrayList<>();
            }
            if (entry.seedOf == null) {
                entry.seedOf = new LinkedHashMap<>();
            }
            if (entry.left == null) {
                entry.left = new ArrayList<>();
            }
            if (entry.arrivals == null) {
                entry.arrivals = new ArrayList<>();
            }
            entry.seeds.removeIf(seed -> seed == null);
            entry.seedOf.values().removeIf(seed -> seed == null);
            entry.left.removeIf(spot -> spot == null || spot.dimension == null);
            entry.arrivals.removeIf(spot -> spot == null || spot.dimension == null);
        }
        if (find(ROOT) == null) {
            document.worlds.add(0, new Entry());
        }
        if (document.last == null || find(document.last) == null) {
            document.last = ROOT;
        }
    }

    private static boolean named(String id) {
        return id != null && (id.isEmpty() || id.equals(MapWorld.clean(id)));
    }

    private Entry find(String id) {
        for (Entry entry : document.worlds) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public Path folder(String id) {
        return id.isEmpty() ? root : root.resolve("worlds").resolve(id);
    }

    public synchronized List<String> ids() {
        List<String> ids = new ArrayList<>();
        for (Entry entry : document.worlds) {
            ids.add(entry.id);
        }
        return ids;
    }

    private Long usable(Long seed) {
        return seed == null || document.scrambled || document.shared.contains(seed) ? null : seed;
    }

    public synchronized String bySeed(Long seed) {
        Long wanted = usable(seed);
        if (wanted == null) {
            return null;
        }
        for (Entry entry : document.worlds) {
            if (entry.seeds.contains(wanted)) {
                return entry.id;
            }
        }
        return null;
    }

    private String create() {
        int number = 2;
        while (find(Integer.toString(number)) != null || Files.exists(folder(Integer.toString(number)))) {
            number++;
        }
        Entry entry = new Entry();
        entry.id = Integer.toString(number);
        document.worlds.add(entry);
        return entry.id;
    }

    public String resolve(Sight sight, boolean boundary, String previous, Names biomes) {
        Map<String, Terrain> terrain = new HashMap<>();
        for (String id : ids()) {
            terrain.put(id, compare(MapWorld.directory(folder(id), sight.dimension(), sight.layer()),
                    sight.chunks(), biomes));
        }
        synchronized (this) {
            String bySeed = bySeed(sight.seed());
            if (bySeed != null && terrain.get(bySeed) == Terrain.OTHER) {
                share(sight.seed());
            }
            Long seed = usable(sight.seed());
            String best = null;
            int bestPoints = 0;
            for (Entry entry : document.worlds) {
                Terrain seen = terrain.getOrDefault(entry.id, Terrain.UNKNOWN);
                if (seen == Terrain.OTHER) {
                    continue;
                }
                int points = seen == Terrain.SAME ? TERRAIN_POINTS : 0;
                if (seed != null && entry.seeds.contains(seed)) {
                    points += SEED_POINTS;
                }
                if (entry.near(sight)) {
                    points += PLACE_POINTS;
                }
                Long recorded = entry.seedOf.get(sight.dimension());
                if (seed != null && recorded != null && !recorded.equals(seed)) {
                    points -= PLACE_POINTS;
                }
                if (points > bestPoints || points > 0 && points == bestPoints && preferred(entry.id, best,
                        boundary ? null : previous)) {
                    best = entry.id;
                    bestPoints = points;
                }
            }
            if (best == null) {
                best = fallback(seed, boundary, previous, terrain);
            }
            record(find(best), sight);
            return best;
        }
    }

    private boolean preferred(String id, String over, String previous) {
        if (id.equals(previous) || over.equals(previous)) {
            return id.equals(previous);
        }
        return id.equals(document.last);
    }

    private String fallback(Long seed, boolean boundary, String previous, Map<String, Terrain> terrain) {
        if (!boundary && previous != null && find(previous) != null && open(terrain.get(previous))) {
            return previous;
        }
        for (Entry entry : document.worlds) {
            if (!entry.claimed() && open(terrain.get(entry.id))) {
                return entry.id;
            }
        }
        if (seed == null && open(terrain.get(document.last))) {
            return document.last;
        }
        return create();
    }

    private static boolean open(Terrain terrain) {
        return terrain == null || terrain == Terrain.UNKNOWN || terrain == Terrain.SAME;
    }

    private void share(Long seed) {
        if (!document.shared.contains(seed)) {
            document.shared.add(seed);
        }
        forget(seed);
    }

    private void forget(Long seed) {
        for (Entry entry : document.worlds) {
            entry.seeds.remove(seed);
            entry.seedOf.values().removeIf(seed::equals);
        }
    }

    private void record(Entry entry, Sight sight) {
        Long seed = usable(sight.seed());
        if (seed != null) {
            Long recorded = entry.seedOf.get(sight.dimension());
            if (recorded != null && !recorded.equals(seed)) {
                document.scrambled = true;
                for (Entry other : document.worlds) {
                    other.seeds.clear();
                    other.seedOf.clear();
                }
            } else {
                entry.seedOf.put(sight.dimension(), seed);
                if (!entry.seeds.contains(seed)) {
                    entry.seeds.add(seed);
                    while (entry.seeds.size() > SEEDS) {
                        entry.seeds.remove(0);
                    }
                }
            }
        }
        if (!entry.near(sight)) {
            entry.arrivals.add(spot(sight.dimension(), sight.x(), sight.y(), sight.z()));
            while (entry.arrivals.size() > ARRIVALS) {
                entry.arrivals.remove(0);
            }
        }
        document.last = entry.id;
        save();
    }

    private static Spot spot(String dimension, int x, int y, int z) {
        Spot spot = new Spot();
        spot.dimension = dimension;
        spot.x = x;
        spot.y = y;
        spot.z = z;
        return spot;
    }

    public synchronized void leave(String id, String dimension, int x, int y, int z) {
        Entry entry = find(id);
        if (entry == null) {
            return;
        }
        entry.left.removeIf(spot -> spot.dimension.equals(dimension));
        entry.left.add(spot(dimension, x, y, z));
        save();
    }

    public synchronized String assign(String id, Sight sight) {
        Entry entry = id == null ? null : find(id);
        if (entry == null) {
            entry = find(create());
        }
        Long seed = usable(sight.seed());
        for (Entry other : document.worlds) {
            if (other == entry) {
                continue;
            }
            if (seed != null) {
                other.seeds.remove(seed);
                other.seedOf.values().removeIf(seed::equals);
            }
            other.left.removeIf(spot -> spot.near(sight.dimension(), sight.x(), sight.y(), sight.z()));
            other.arrivals.removeIf(spot -> spot.near(sight.dimension(), sight.x(), sight.y(), sight.z()));
        }
        if (seed != null) {
            entry.seedOf.remove(sight.dimension());
        }
        record(entry, sight);
        return entry.id;
    }

    public static Terrain compare(Path directory, List<ChunkData> chunks, Names biomes) {
        Map<Long, Region> regions = new HashMap<>();
        Map<Integer, Integer> kinds = new HashMap<>();
        Names blocks = new Names(65535);
        int columns = 0;
        int sameHeights = 0;
        int named = 0;
        int sameBiomes = 0;
        for (ChunkData chunk : chunks) {
            int rx = chunk.cx >> 5;
            int rz = chunk.cz >> 5;
            long key = Region.key(rx, rz);
            if (!regions.containsKey(key)) {
                regions.put(key, read(MapStore.file(directory, rx, rz), rx, rz, blocks, biomes));
            }
            Region region = regions.get(key);
            if (region == null) {
                continue;
            }
            int baseX = (chunk.cx << 4) & Region.MASK;
            int baseZ = (chunk.cz << 4) & Region.MASK;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int from = (z << 4) | x;
                    int to = ((baseZ + z) << Region.BITS) | (baseX + x);
                    if (chunk.height[from] == Region.NONE || region.height[to] == Region.NONE) {
                        continue;
                    }
                    columns++;
                    if (Math.abs(chunk.height[from] - region.height[to]) <= 1
                            && Math.abs((chunk.depth[from] & 0xFF) - (region.depth[to] & 0xFF)) <= 2) {
                        sameHeights++;
                    }
                    int biome = chunk.biome[from] & 0xFFFF;
                    if (biome == 0 || region.biome[to] == 0) {
                        continue;
                    }
                    named++;
                    kinds.merge(biome, 1, Integer::sum);
                    if (biome == (region.biome[to] & 0xFFFF)) {
                        sameBiomes++;
                    }
                }
            }
        }
        if (columns < ENOUGH) {
            return Terrain.UNKNOWN;
        }
        double heights = sameHeights / (double) columns;
        boolean biomesKnown = named >= ENOUGH;
        double shared = biomesKnown ? sameBiomes / (double) named : 0.0;
        if (biomesKnown && shared < OTHER_BIOMES) {
            return Terrain.OTHER;
        }
        if (heights >= SAME_HEIGHTS || biomesKnown && shared >= SAME_BIOMES && varied(kinds, named)) {
            return Terrain.SAME;
        }
        if (heights > OTHER_HEIGHTS) {
            return Terrain.UNKNOWN;
        }
        return biomesKnown && shared >= SAME_BIOMES ? Terrain.DOUBT : Terrain.OTHER;
    }

    private static boolean varied(Map<Integer, Integer> kinds, int total) {
        int most = 0;
        for (int count : kinds.values()) {
            most = Math.max(most, count);
        }
        return total - most >= total * VARIED;
    }

    private static Region read(Path file, int rx, int rz, Names blocks, Names biomes) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return RegionCodec.decode(Files.readAllBytes(file), rx, rz, blocks, biomes);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private void save() {
        try {
            Files.createDirectories(root);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(document, writer);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not save {}: {}", file, exception.getMessage());
        }
    }
}
