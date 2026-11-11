package com.github.olympus.atlas.client;

import com.github.olympus.atlas.core.map.MapStore;
import com.github.olympus.atlas.core.render.MapPages;
import com.github.olympus.atlas.core.render.MapPainter;
import com.github.olympus.proteus.client.ui.Canvas;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public final class PageLayer {
    private static final int UPLOADS = 6;
    private static final int SPARE_TEXTURES = 12;
    private static final int SPARE_BUFFERS = 12;
    private static final int KEEP = 64;
    private static final long CHECK_NANOS = 200_000_000L;
    private static final long LINGER_NANOS = 2_000_000_000L;

    private static final class Page {
        final int level;
        final int px;
        final int pz;
        MapTexture texture;
        volatile int[] ready;
        volatile boolean painting;
        volatile boolean dropped;
        boolean painted;
        long stamp;
        long checkedAt;
        long usedAt;
        double distance;

        Page(int level, int px, int pz) {
            this.level = level;
            this.px = px;
            this.pz = pz;
        }
    }

    private final ExecutorService workers;
    private final int limit;
    private final Map<Long, Page> pages = new HashMap<>();
    private final List<Page> visible = new ArrayList<>();
    private final List<Page> behind = new ArrayList<>();
    private final ArrayDeque<MapTexture> textures = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<int[]> buffers = new ConcurrentLinkedQueue<>();
    private final AtomicInteger spare = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private MapStore store;
    private int background;
    private int names;
    private boolean failed;

    public PageLayer(ExecutorService workers, int threads) {
        this.workers = workers;
        this.limit = Math.max(1, threads);
    }

    public boolean usable() {
        return !failed;
    }

    public void draw(Canvas canvas, MapStore source, double centerX, double centerZ, double scale, int width,
            int height, double guiScale, int fill) {
        if (source != store || fill != background) {
            clear();
            store = source;
            background = fill;
        }
        long now = System.nanoTime();
        double screenWidth = width * guiScale;
        double screenHeight = height * guiScale;
        int level = MapPainter.level(scale);
        int firstX = MapPages.first(centerX, screenWidth, scale, level);
        int lastX = MapPages.last(centerX, screenWidth, scale, level);
        int firstZ = MapPages.first(centerZ, screenHeight, scale, level);
        int lastZ = MapPages.last(centerZ, screenHeight, scale, level);
        double blocks = MapPages.blocks(level);
        visible.clear();
        for (int pz = firstZ; pz <= lastZ; pz++) {
            for (int px = firstX; px <= lastX; px++) {
                int x = px;
                int z = pz;
                Page page = pages.computeIfAbsent(MapPages.key(level, px, pz), key -> new Page(level, x, z));
                page.usedAt = now;
                page.distance = Math.hypot((px + 0.5) * blocks - centerX, (pz + 0.5) * blocks - centerZ);
                visible.add(page);
            }
        }
        visible.sort(Comparator.comparingDouble(page -> page.distance));
        boolean complete = true;
        int uploads = 0;
        for (Page page : visible) {
            refresh(page, now);
            if (page.ready != null && uploads < UPLOADS) {
                uploads++;
                upload(page);
            }
            complete &= page.texture != null;
        }
        if (!complete) {
            behind.clear();
            for (Page page : pages.values()) {
                if (page.level != level && page.texture != null) {
                    behind.add(page);
                }
            }
            behind.sort(Comparator.comparingInt((Page page) -> page.level).reversed());
            for (Page page : behind) {
                if (place(canvas, page, centerX, centerZ, scale, screenWidth, screenHeight, guiScale)) {
                    page.usedAt = now;
                }
            }
        }
        for (Page page : visible) {
            place(canvas, page, centerX, centerZ, scale, screenWidth, screenHeight, guiScale);
        }
        trim(now);
    }

    private void refresh(Page page, long now) {
        if (page.painting || page.ready != null || failed) {
            return;
        }
        long stamp;
        if (!page.painted) {
            stamp = stamp(page);
        } else {
            if (now - page.checkedAt < CHECK_NANOS) {
                return;
            }
            page.checkedAt = now;
            stamp = stamp(page);
            if (stamp == page.stamp) {
                return;
            }
        }
        if (inFlight.get() >= limit) {
            return;
        }
        MapStore source = store;
        int fill = background;
        page.painting = true;
        page.painted = true;
        page.stamp = stamp;
        page.checkedAt = now;
        inFlight.incrementAndGet();
        try {
            workers.execute(() -> {
                int[] pixels = buffers.poll();
                if (pixels == null) {
                    pixels = new int[MapPages.AREA];
                } else {
                    spare.decrementAndGet();
                }
                try {
                    MapPages.paint(source, page.px, page.pz, page.level, fill, pixels);
                    MapTexture.abgr(pixels, 0, pixels.length);
                    if (page.dropped) {
                        recycle(pixels);
                    } else {
                        page.ready = pixels;
                    }
                } catch (RuntimeException exception) {
                    recycle(pixels);
                    AtlasClient.LOGGER.debug("Atlas could not draw map page {} {}", page.px, page.pz, exception);
                } finally {
                    page.painting = false;
                    inFlight.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException exception) {
            page.painting = false;
            page.painted = false;
            inFlight.decrementAndGet();
        }
    }

    private long stamp(Page page) {
        return store.stamp(page.px << page.level, page.pz << page.level, 1 << page.level);
    }

    private void recycle(int[] pixels) {
        if (spare.get() < SPARE_BUFFERS) {
            spare.incrementAndGet();
            buffers.add(pixels);
        }
    }

    private void upload(Page page) {
        int[] pixels = page.ready;
        page.ready = null;
        if (page.texture == null) {
            page.texture = textures.poll();
            if (page.texture == null) {
                page.texture = new MapTexture("page/" + names++);
            }
        }
        page.texture.upload(pixels, MapPages.SIZE, MapPages.SIZE);
        recycle(pixels);
        if (page.texture.failed()) {
            failed = true;
        }
    }

    private boolean place(Canvas canvas, Page page, double centerX, double centerZ, double scale,
            double screenWidth, double screenHeight, double guiScale) {
        if (page.texture == null || !page.texture.ready()) {
            return false;
        }
        int left = MapPages.edge(page.px, page.level, centerX, scale, screenWidth);
        int right = MapPages.edge(page.px + 1, page.level, centerX, scale, screenWidth);
        int top = MapPages.edge(page.pz, page.level, centerZ, scale, screenHeight);
        int bottom = MapPages.edge(page.pz + 1, page.level, centerZ, scale, screenHeight);
        if (right <= 0 || bottom <= 0 || left >= screenWidth || top >= screenHeight || right <= left
                || bottom <= top) {
            return false;
        }
        page.texture.draw(canvas, (float) (left / guiScale), (float) (top / guiScale),
                (float) ((right - left) / guiScale), (float) ((bottom - top) / guiScale));
        return true;
    }

    private void trim(long now) {
        int allowed = Math.max(KEEP, visible.size() * 2);
        boolean crowded = pages.size() > allowed;
        long oldest = Long.MAX_VALUE;
        if (crowded) {
            List<Page> idle = new ArrayList<>();
            for (Page page : pages.values()) {
                if (page.usedAt != now) {
                    idle.add(page);
                }
            }
            idle.sort(Comparator.comparingLong(page -> page.usedAt));
            int excess = pages.size() - allowed;
            if (excess < idle.size()) {
                oldest = idle.get(excess).usedAt;
            }
        }
        Iterator<Page> iterator = pages.values().iterator();
        while (iterator.hasNext()) {
            Page page = iterator.next();
            if (page.usedAt == now) {
                continue;
            }
            if (now - page.usedAt > LINGER_NANOS || (crowded && page.usedAt < oldest)) {
                iterator.remove();
                drop(page);
            }
        }
    }

    private void drop(Page page) {
        page.dropped = true;
        int[] pixels = page.ready;
        page.ready = null;
        if (pixels != null) {
            recycle(pixels);
        }
        if (page.texture == null) {
            return;
        }
        if (textures.size() < SPARE_TEXTURES && !page.texture.failed()) {
            textures.add(page.texture);
        } else {
            page.texture.close();
        }
        page.texture = null;
    }

    private void clear() {
        for (Page page : pages.values()) {
            drop(page);
        }
        pages.clear();
        visible.clear();
        behind.clear();
    }

    public void close() {
        clear();
        for (MapTexture texture : textures) {
            texture.close();
        }
        textures.clear();
        buffers.clear();
        spare.set(0);
        store = null;
    }
}
