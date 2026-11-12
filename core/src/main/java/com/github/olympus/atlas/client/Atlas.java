package com.github.olympus.atlas.client;

import com.github.olympus.atlas.client.ui.MapApp;
import com.github.olympus.atlas.core.AtlasConfig;
import com.github.olympus.atlas.core.fair.FairPlay;
import com.github.olympus.atlas.core.map.ChunkData;
import com.github.olympus.atlas.core.map.MapStore;
import com.github.olympus.atlas.core.map.MapWorld;
import com.github.olympus.atlas.core.map.Region;
import com.github.olympus.atlas.core.map.Style;
import com.github.olympus.atlas.core.map.SubWorlds;
import com.github.olympus.atlas.core.waypoint.Waypoint;
import com.github.olympus.atlas.core.waypoint.WaypointStore;
import com.github.olympus.proteus.client.ProteusScreen;
import com.github.olympus.proteus.client.ui.ToastOverlay;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;

public final class Atlas {
    private static final WaypointStore EMPTY = new WaypointStore(null);
    private static final FairPlay FAIR = new FairPlay();
    private static final AtomicInteger IN_FLIGHT = new AtomicInteger();
    private static final List<World.Blip> BLIPS = new ArrayList<>();
    private static final Map<Long, Integer> SCANNED = new HashMap<>();
    private static final Map<Long, Integer> SCANNED_CAVE = new HashMap<>();
    private static final Map<String, Boolean> OCCLUDED = new HashMap<>();
    private static final double NEAR = 64.0;
    private static final int PROBE_CHUNKS = 5;
    private static final int PROBE_RADIUS = 3;
    private static final int NOTE_TICKS = 100;
    private static final int NOTE_BLOCKS = 8;
    private static final int PROBE_WAIT = 20;
    private static final int PROBE_LIMIT = 200;

    private static final class Seek {
        final Long seed;
        final boolean split;
        final boolean boundary;
        final String previous;
        boolean checked;
        boolean submitted;
        int age;
        volatile String result;

        Seek(Long seed, boolean split, boolean boundary, String previous) {
            this.seed = seed;
            this.split = split;
            this.boundary = boundary;
            this.previous = previous;
        }
    }

    private static AtlasConfig config;
    private static ExecutorService work;
    private static ExecutorService io;
    private static MapStore.Shared shared;
    private static volatile MapWorld world;
    private static volatile WaypointStore waypoints;
    private static volatile SubWorlds index;
    private static volatile int epoch;
    private static String session;
    private static String located;
    private static Seek seek;
    private static Object lastLink;
    private static Long seed;
    private static boolean split;
    private static int[] spot;
    private static int[] noted;
    private static String spotDimension;
    private static volatile String forced;
    private static String dimension = "minecraft:overworld";
    private static ClientLevel lastLevel;
    private static int ticks;
    private static volatile long tickNanos = System.nanoTime();
    private static int[] spiral = new int[0];
    private static int spiralRadius = -1;
    private static int cursor;
    private static boolean wasDead;
    private static boolean underground;
    private static int caveLayer = MapWorld.SURFACE;
    private static boolean ceiling;
    private static boolean colorsLoaded;
    private static boolean showWaypoints = true;
    private static boolean identifying;

    private Atlas() {
    }

    public static synchronized AtlasConfig config() {
        if (config == null) {
            config = AtlasConfig.load(file());
        }
        return config;
    }

    public static void save() {
        AtlasConfig current = config();
        current.sanitize();
        Path file = file();
        executors();
        io.execute(() -> current.save(file));
        apply();
    }

    private static Path file() {
        return AtlasClient.configDirectory().resolve("atlas.json");
    }

    private static synchronized void executors() {
        if (work != null) {
            return;
        }
        int threads = config().threads();
        work = Executors.newFixedThreadPool(threads, factory("atlas-worker"));
        io = Executors.newSingleThreadExecutor(factory("atlas-io"));
        shared = new MapStore.Shared(work, io);
        apply();
    }

    private static ThreadFactory factory(String name) {
        AtomicInteger count = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, name + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
    }

    private static void apply() {
        if (shared == null) {
            return;
        }
        Style style = Style.of(config());
        if (!style.equals(shared.style)) {
            shared.style = style;
        }
        shared.limit = (long) config().memoryLimitMb << 20;
    }

    public static MapStore.Shared shared() {
        executors();
        return shared;
    }

    public static ExecutorService workers() {
        executors();
        return work;
    }

    public static int threads() {
        return config().threads();
    }

    public static MapWorld world() {
        return world;
    }

    public static List<String> worlds() {
        SubWorlds known = index;
        return known == null || !split || world == null || seek != null ? List.of() : known.ids();
    }

    public static String located() {
        return located;
    }

    public static void assign(String id) {
        SubWorlds known = index;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (known == null || player == null || !split || seek != null) {
            return;
        }
        BlockPos pos = player.blockPosition();
        SubWorlds.Sight sight = new SubWorlds.Sight(seed, dimension, MapWorld.SURFACE, pos.getX(), pos.getY(),
                pos.getZ(), List.of());
        io.execute(() -> forced = known.assign(id, sight));
    }

    public static String label() {
        String name = session == null ? "world" : session;
        return located == null || located.isEmpty() ? name : name + "-" + located;
    }

    public static WaypointStore waypoints() {
        WaypointStore store = waypoints;
        return store == null ? EMPTY : store;
    }

    public static FairPlay fair() {
        return FAIR;
    }

    public static String dimension() {
        return dimension;
    }

    public static boolean ceiling() {
        return ceiling;
    }

    public static List<World.Blip> blips() {
        return BLIPS;
    }

    public static boolean identifying() {
        return identifying;
    }

    public static boolean showWaypoints() {
        return showWaypoints;
    }

    public static float partial() {
        return Math.max(0f, Math.min(1f, (System.nanoTime() - tickNanos) / 50_000_000f));
    }

    public static boolean minimapAllowed() {
        return !config().honorServerRules || FAIR.minimap();
    }

    public static boolean radarAllowed() {
        return !config().honorServerRules || FAIR.radar();
    }

    public static boolean cavesAllowed() {
        return !config().honorServerRules || FAIR.caves(ceiling);
    }

    public static boolean caveActive() {
        AtlasConfig.CaveMode mode = config().caveMode;
        if (mode == AtlasConfig.CaveMode.OFF || !cavesAllowed()) {
            return false;
        }
        return mode == AtlasConfig.CaveMode.ON || ceiling || underground;
    }

    public static int layer() {
        return caveActive() ? caveLayer : MapWorld.SURFACE;
    }

    public static MapStore store(String dimensionId, int layer) {
        MapWorld current = world;
        return current == null ? null : current.store(dimensionId, layer);
    }

    public static MapStore store() {
        return store(dimension, layer());
    }

    public static boolean occluded(Waypoint waypoint) {
        return Boolean.TRUE.equals(OCCLUDED.get(waypoint.id));
    }

    private static boolean hidden(ClientLevel level, LocalPlayer player, MapStore surface, double x, double y,
            double z, double tx, double ty, double tz) {
        double dx = tx - x;
        double dy = ty - y;
        double dz = tz - z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.5) {
            return false;
        }
        double close = Math.min(1.0, NEAR / length);
        if (World.blocked(level, player, x, y, z, x + dx * close, y + dy * close, z + dz * close)) {
            return true;
        }
        if (surface == null || length <= NEAR) {
            return false;
        }
        double flat = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.min(400.0, (flat * (1.0 - close)) / 1.5);
        for (int step = 1; step < steps; step++) {
            double t = close + (1.0 - close) * step / steps;
            int top = surface.height((int) Math.floor(x + dx * t), (int) Math.floor(z + dz * t));
            if (top != Region.NONE && top + 1 > y + dy * t) {
                return true;
            }
        }
        return false;
    }

    public static void chat(String message) {
        if (FAIR.read(message)) {
            AtlasClient.LOGGER.info("Atlas: the server changed the map rules");
        }
    }

    public static String text(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    public static void tick(Minecraft minecraft) {
        ticks++;
        tickNanos = System.nanoTime();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            if (session != null) {
                leave();
            }
            return;
        }
        executors();
        String key = World.session(minecraft);
        if (!key.equals(session)) {
            leave();
            join(minecraft, key);
        }
        if (level != lastLevel) {
            String before = lastLevel == null ? null : dimension;
            lastLevel = level;
            dimension = Ids.dimension(level);
            Object link = World.link(minecraft);
            remember();
            split = World.remote(minecraft) && config().subWorlds;
            seed = split ? World.seed(level) : null;
            seek = new Seek(seed, split, link != lastLink || dimension.equals(before), located);
            lastLink = link;
            ceiling = World.ceiling(level);
            SCANNED.clear();
            SCANNED_CAVE.clear();
            underground = false;
            World.forget();
            World.biomes(level, shared);
        }
        if (!colorsLoaded) {
            colorsLoaded = true;
            World.blocks(shared);
            List<String> names = World.blockNames();
            work.execute(() -> BlockColorLoader.load(names, shared));
        }
        if (seek != null) {
            seek(level, player);
        } else {
            follow(player);
        }
        keys(minecraft, player);
        if (ticks % 10 == 0) {
            environment(level, player);
        }
        scan(minecraft, level, player);
        deaths(player);
        AtlasConfig settings = config();
        boolean radar = radarAllowed() && settings.minimapEnabled;
        double range = settings.minimapSize / settings.minimapScale();
        identifying = radar && settings.radarIdentify && World.identifying(minecraft);
        World.blips(minecraft, radar && settings.radarPlayers, radar && settings.radarHostile,
                radar && settings.radarPassive, settings.radarHideSneaking, settings.radarHideInvisible,
                identifying, range, BLIPS);
        if (ticks % 20 == 0) {
            housekeeping(player);
        }
    }

    private static void join(Minecraft minecraft, String key) {
        session = key;
        Path root = minecraft.gameDirectory.toPath().resolve("atlas").resolve(key);
        FAIR.reset();
        wasDead = false;
        io.execute(() -> index = new SubWorlds(root));
    }

    private static void leave() {
        close();
        index = null;
        session = null;
        located = null;
        seek = null;
        forced = null;
        split = false;
        seed = null;
        lastLink = null;
        lastLevel = null;
        BLIPS.clear();
        FAIR.reset();
    }

    private static void seek(ClientLevel level, LocalPlayer player) {
        SubWorlds known = index;
        Seek current = seek;
        if (known == null) {
            return;
        }
        if (!current.split) {
            open(known, SubWorlds.ROOT);
            seek = null;
            return;
        }
        if (!current.checked) {
            current.checked = true;
            String id = known.bySeed(current.seed);
            if (id != null) {
                open(known, id);
            } else {
                close();
            }
        }
        if (current.submitted) {
            String id = current.result;
            if (id != null) {
                open(known, id);
                seek = null;
            }
            return;
        }
        current.age++;
        int centerX = player.blockPosition().getX() >> 4;
        int centerZ = player.blockPosition().getZ() >> 4;
        List<LevelChunk> chunks = new ArrayList<>();
        LevelChunk here = World.chunk(level, centerX, centerZ);
        if (here != null) {
            chunks.add(here);
        }
        List<int[]> spots = new ArrayList<>();
        spots.add(new int[] {centerX, centerZ});
        for (int dz = -PROBE_RADIUS; dz <= PROBE_RADIUS && here != null; dz++) {
            for (int dx = -PROBE_RADIUS; dx <= PROBE_RADIUS; dx++) {
                LevelChunk near = dx == 0 && dz == 0 ? null : World.chunk(level, centerX + dx, centerZ + dz);
                if (near != null) {
                    chunks.add(near);
                    spots.add(new int[] {centerX + dx, centerZ + dz});
                }
            }
        }
        boolean enough = here != null && (chunks.size() >= PROBE_CHUNKS || current.age > PROBE_WAIT);
        if (!enough && current.age <= PROBE_LIMIT) {
            return;
        }
        current.submitted = true;
        int slice = ceiling ? MapWorld.layer(player.blockPosition().getY()) : MapWorld.SURFACE;
        int minY = Heights.minY(level);
        int maxY = Heights.maxY(level);
        BlockPos pos = player.blockPosition();
        String place = dimension;
        work.execute(() -> {
            List<ChunkData> scanned = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                try {
                    scanned.add(World.scan(chunks.get(i), spots.get(i)[0], spots.get(i)[1], minY, maxY, slice,
                            shared));
                } catch (RuntimeException exception) {
                    AtlasClient.LOGGER.debug("Atlas could not scan a chunk to recognise the world", exception);
                }
            }
            io.execute(() -> {
                try {
                    current.result = known.resolve(new SubWorlds.Sight(current.seed, place, slice, pos.getX(),
                            pos.getY(), pos.getZ(), scanned), current.boundary, current.previous, shared.biomes);
                } catch (RuntimeException exception) {
                    AtlasClient.LOGGER.warn("Atlas could not tell which world this is", exception);
                    current.result = current.previous == null ? SubWorlds.ROOT : current.previous;
                }
            });
        });
    }

    private static void follow(LocalPlayer player) {
        SubWorlds known = index;
        String chosen = forced;
        if (chosen != null && known != null) {
            forced = null;
            spot = null;
            open(known, chosen);
        }
        if (world == null || !split) {
            return;
        }
        BlockPos pos = player.blockPosition();
        spot = new int[] {pos.getX(), pos.getY(), pos.getZ()};
        spotDimension = dimension;
        if (ticks % NOTE_TICKS == 0 && (noted == null || Math.abs(noted[0] - spot[0]) + Math.abs(noted[1] - spot[1])
                + Math.abs(noted[2] - spot[2]) >= NOTE_BLOCKS)) {
            int[] at = spot;
            String id = located;
            String place = dimension;
            noted = at;
            io.execute(() -> known.leave(id, place, at[0], at[1], at[2]));
        }
    }

    private static void remember() {
        SubWorlds known = index;
        int[] at = spot;
        String id = located;
        String place = spotDimension;
        spot = null;
        noted = null;
        if (known != null && at != null && id != null && world != null && io != null) {
            io.execute(() -> known.leave(id, place, at[0], at[1], at[2]));
        }
    }

    private static void open(SubWorlds known, String id) {
        if (world != null && id.equals(located)) {
            return;
        }
        close();
        located = id;
        Path root = known.folder(id);
        int opened = epoch;
        world = new MapWorld(root, shared);
        io.execute(() -> {
            WaypointStore loaded = new WaypointStore(root.resolve("waypoints.json"));
            if (opened == epoch) {
                waypoints = loaded;
            }
        });
    }

    private static void close() {
        remember();
        MapWorld closing = world;
        WaypointStore saving = waypoints;
        epoch++;
        world = null;
        waypoints = null;
        SCANNED.clear();
        SCANNED_CAVE.clear();
        OCCLUDED.clear();
        if (io != null) {
            io.execute(() -> {
                if (saving != null) {
                    saving.saveIfDirty();
                }
                if (closing != null) {
                    closing.close();
                }
            });
        }
    }

    private static void keys(Minecraft minecraft, LocalPlayer player) {
        KeyBindings keys = AtlasClient.keys();
        AtlasConfig settings = config();
        while (keys.map.consumeClick()) {
            if (Compat.screen(minecraft) == null) {
                Compat.show(minecraft, new ProteusScreen(Component.translatable("atlas.map.title"), null,
                        MapApp::new));
            }
        }
        while (keys.menu.consumeClick()) {
            if (Compat.screen(minecraft) == null) {
                Compat.show(minecraft, AtlasMenu.create(null));
            }
        }
        while (keys.waypoint.consumeClick()) {
            quick(player, Waypoint.Kind.NORMAL);
        }
        while (keys.temporary.consumeClick()) {
            quick(player, Waypoint.Kind.TEMPORARY);
        }
        boolean changed = false;
        while (keys.minimap.consumeClick()) {
            settings.minimapEnabled = !settings.minimapEnabled;
            changed = true;
        }
        while (keys.zoomIn.consumeClick()) {
            settings.minimapZoom = Math.min(AtlasConfig.MINIMAP_ZOOMS.length - 1, settings.minimapZoom + 1);
            changed = true;
        }
        while (keys.zoomOut.consumeClick()) {
            settings.minimapZoom = Math.max(0, settings.minimapZoom - 1);
            changed = true;
        }
        while (keys.view.consumeClick()) {
            settings.minimapTilt = !settings.minimapTilt;
            changed = true;
        }
        while (keys.cave.consumeClick()) {
            cycleCave();
            changed = true;
        }
        while (keys.waypoints.consumeClick()) {
            showWaypoints = !showWaypoints;
        }
        if (changed) {
            save();
        }
    }

    public static void cycleCave() {
        AtlasConfig.CaveMode[] modes = AtlasConfig.CaveMode.values();
        config().caveMode = modes[(config().caveMode.ordinal() + 1) % modes.length];
        ToastOverlay.show(text("atlas.toast.cave"), text("atlas.cave." + config().caveMode.name().toLowerCase()),
                "filled_map");
    }

    public static boolean matches(KeyMapping key, String pressed) {
        return !key.isUnbound() && key.saveString().equals(pressed);
    }

    private static void quick(LocalPlayer player, Waypoint.Kind kind) {
        WaypointStore store = waypoints;
        if (store == null) {
            return;
        }
        Waypoint waypoint = new Waypoint();
        BlockPos pos = player.blockPosition();
        waypoint.x = pos.getX();
        waypoint.y = pos.getY();
        waypoint.z = pos.getZ();
        waypoint.dimension = dimension;
        waypoint.kind = kind;
        int number = store.all().size() + 1;
        waypoint.name = text(kind == Waypoint.Kind.TEMPORARY ? "atlas.waypoint.temporary" : "atlas.waypoint.numbered",
                number);
        waypoint.color = Waypoint.COLORS[number % Waypoint.COLORS.length];
        if (kind == Waypoint.Kind.TEMPORARY) {
            waypoint.icon = "flag";
        }
        store.add(waypoint);
        ToastOverlay.show(text("atlas.toast.waypoint"), waypoint.name + "  " + waypoint.x + " " + waypoint.y + " "
                + waypoint.z, "filled_map");
    }

    private static void environment(ClientLevel level, LocalPlayer player) {
        BlockPos pos = player.blockPosition();
        caveLayer = MapWorld.layer(pos.getY());
        MapStore surface = store(dimension, MapWorld.SURFACE);
        boolean below = false;
        if (!ceiling && surface != null) {
            int top = surface.height(pos.getX(), pos.getZ());
            below = top != Region.NONE && pos.getY() < top - 6 && World.skyLight(level, pos.above()) == 0;
        }
        underground = below;
        AtlasConfig settings = config();
        OCCLUDED.clear();
        if (settings.waypointOcclusion == AtlasConfig.Occlusion.ALWAYS || surface == null || !Cam.ready()) {
            return;
        }
        for (Waypoint waypoint : waypoints().in(dimension)) {
            OCCLUDED.put(waypoint.id, blocked(surface, Cam.x(), Cam.y(), Cam.z(), waypoint));
        }
    }

    private static boolean blocked(MapStore surface, double x, double y, double z, Waypoint waypoint) {
        double dx = waypoint.x + 0.5 - x;
        double dy = waypoint.y + 1.0 - y;
        double dz = waypoint.z + 0.5 - z;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.min(length - 1.0, 384.0);
        for (int step = 2; step < steps; step++) {
            double t = step / length;
            int top = surface.height((int) Math.floor(x + dx * t), (int) Math.floor(z + dz * t));
            if (top != Region.NONE && top + 1 > y + dy * t) {
                return true;
            }
        }
        return false;
    }

    private static void scan(Minecraft minecraft, ClientLevel level, LocalPlayer player) {
        MapWorld current = world;
        if (current == null || seek != null) {
            return;
        }
        AtlasConfig settings = config();
        int radius = settings.scanRadius > 0 ? settings.scanRadius : Math.min(32, World.renderDistance(minecraft));
        if (radius != spiralRadius) {
            spiral = spiral(radius);
            spiralRadius = radius;
            cursor = 0;
        }
        int limit = threads() * 2;
        int centerX = player.blockPosition().getX() >> 4;
        int centerZ = player.blockPosition().getZ() >> 4;
        int minY = Heights.minY(level);
        int maxY = Heights.maxY(level);
        boolean cave = caveActive();
        int slice = caveLayer;
        MapStore surface = ceiling ? null : current.store(dimension, MapWorld.SURFACE);
        MapStore layered = cave ? current.store(dimension, slice) : null;
        int count = spiral.length / 2;
        for (int step = 0; step < 64 && count > 0; step++) {
            if (IN_FLIGHT.get() >= limit) {
                break;
            }
            int index = cursor;
            cursor = (cursor + 1) % count;
            int dx = spiral[index * 2];
            int dz = spiral[index * 2 + 1];
            int cx = centerX + dx;
            int cz = centerZ + dz;
            int distance = Math.max(Math.abs(dx), Math.abs(dz));
            long key = Region.key(cx, cz);
            boolean top = surface != null && due(SCANNED.get(key), distance);
            boolean under = layered != null && due(SCANNED_CAVE.get(key ^ ((long) slice << 48)), distance);
            if (!top && !under) {
                continue;
            }
            LevelChunk chunk = World.chunk(level, cx, cz);
            if (chunk == null) {
                continue;
            }
            if (top) {
                SCANNED.put(key, ticks);
                submit(chunk, cx, cz, minY, maxY, MapWorld.SURFACE, surface);
            }
            if (under) {
                SCANNED_CAVE.put(key ^ ((long) slice << 48), ticks);
                submit(chunk, cx, cz, minY, maxY, slice, layered);
            }
        }
        if (ticks % 400 == 0) {
            prune(SCANNED, centerX, centerZ, radius + 6, false);
            prune(SCANNED_CAVE, centerX, centerZ, radius + 6, true);
        }
    }

    private static boolean due(Integer last, int distance) {
        if (last == null) {
            return true;
        }
        int age = ticks - last;
        if (distance <= 1) {
            return age >= 20;
        }
        if (distance <= 4) {
            return age >= 100;
        }
        if (distance <= 10) {
            return age >= 400;
        }
        return age >= 2400;
    }

    private static void submit(LevelChunk chunk, int cx, int cz, int minY, int maxY, int slice, MapStore store) {
        IN_FLIGHT.incrementAndGet();
        work.execute(() -> {
            try {
                ChunkData data = World.scan(chunk, cx, cz, minY, maxY, slice, shared);
                store.apply(data);
            } catch (RuntimeException exception) {
                AtlasClient.LOGGER.debug("Atlas could not scan chunk {} {}", cx, cz, exception);
            } finally {
                IN_FLIGHT.decrementAndGet();
            }
        });
    }

    private static void prune(Map<Long, Integer> scanned, int centerX, int centerZ, int radius, boolean aged) {
        Iterator<Map.Entry<Long, Integer>> iterator = scanned.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Integer> entry = iterator.next();
            if (aged) {
                if (ticks - entry.getValue() > 6000) {
                    iterator.remove();
                }
                continue;
            }
            long key = entry.getKey();
            int cx = (int) (key >> 32);
            int cz = (int) key;
            if (Math.abs(cx - centerX) > radius || Math.abs(cz - centerZ) > radius) {
                iterator.remove();
            }
        }
    }

    private static int[] spiral(int radius) {
        List<int[]> offsets = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                offsets.add(new int[] {dx, dz});
            }
        }
        offsets.sort((a, b) -> Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1]));
        int[] flat = new int[offsets.size() * 2];
        for (int i = 0; i < offsets.size(); i++) {
            flat[i * 2] = offsets.get(i)[0];
            flat[i * 2 + 1] = offsets.get(i)[1];
        }
        return flat;
    }

    private static void deaths(LocalPlayer player) {
        boolean dead = World.dead(player);
        if (dead && !wasDead && config().deathWaypoints) {
            WaypointStore store = waypoints;
            if (store != null) {
                Waypoint waypoint = new Waypoint();
                BlockPos pos = player.blockPosition();
                waypoint.x = pos.getX();
                waypoint.y = pos.getY();
                waypoint.z = pos.getZ();
                waypoint.dimension = dimension;
                waypoint.kind = Waypoint.Kind.DEATH;
                waypoint.icon = "skull";
                waypoint.color = Waypoint.COLORS[0];
                waypoint.group = text("atlas.waypoint.deaths");
                waypoint.name = text("atlas.waypoint.death",
                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
                store.add(waypoint);
                store.trimDeaths(config().deathLimit, config().deathExpiryHours * 3_600_000L,
                        System.currentTimeMillis());
            }
        }
        wasDead = dead;
    }

    private static void housekeeping(LocalPlayer player) {
        AtlasConfig settings = config();
        WaypointStore store = waypoints;
        long now = System.currentTimeMillis();
        if (store != null) {
            store.trimTemporary(settings.temporaryExpiryMinutes * 60_000L, now, dimension, player.getX(),
                    player.getY(), player.getZ(), settings.temporaryRemoveOnArrival);
            if (settings.deathExpiryHours > 0) {
                store.trimDeaths(settings.deathLimit, settings.deathExpiryHours * 3_600_000L, now);
            }
        }
        MapWorld current = world;
        long interval = settings.saveIntervalSeconds * 1_000_000_000L;
        io.execute(() -> {
            if (store != null) {
                store.saveIfDirty();
            }
            if (current != null) {
                current.maintain(interval);
            }
        });
    }
}
