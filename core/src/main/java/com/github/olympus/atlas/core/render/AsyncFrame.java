package com.github.olympus.atlas.core.render;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public final class AsyncFrame<P> {
    public interface Painter<P> {
        void paint(P params, Frame<P> frame, int rowFrom, int rowTo);
    }

    public interface Finisher<P> {
        void finish(P params, Frame<P> frame);
    }

    public static final class Frame<P> {
        public final int[] pixels;
        public final int width;
        public final int height;
        public float[] depth;
        public P params;
        public long serial;

        Frame(int width, int height) {
            this.width = width;
            this.height = height;
            this.pixels = new int[width * height];
        }
    }

    private final ExecutorService executor;
    private final int bands;
    private final Painter<P> painter;
    private final Finisher<P> finisher;
    private final boolean depth;
    private Frame<P> ready;
    private Frame<P> shown;
    private Frame<P> spare;
    private boolean busy;
    private P pending;
    private int pendingWidth;
    private int pendingHeight;
    private long serial;

    public AsyncFrame(ExecutorService executor, int bands, boolean depth, Painter<P> painter, Finisher<P> finisher) {
        this.executor = executor;
        this.bands = Math.max(1, bands);
        this.depth = depth;
        this.painter = painter;
        this.finisher = finisher;
    }

    public synchronized void request(P params, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        pending = params;
        pendingWidth = width;
        pendingHeight = height;
        if (!busy) {
            start();
        }
    }

    public synchronized boolean idle() {
        return !busy && pending == null;
    }

    private void start() {
        P params = pending;
        int width = pendingWidth;
        int height = pendingHeight;
        pending = null;
        Frame<P> frame = spare;
        spare = null;
        if (frame == null || frame.width != width || frame.height != height) {
            frame = new Frame<>(width, height);
        }
        if (depth && frame.depth == null) {
            frame.depth = new float[width * height];
        }
        frame.params = params;
        frame.serial = ++serial;
        busy = true;
        Frame<P> target = frame;
        int count = Math.max(1, Math.min(bands, height / 24));
        AtomicInteger left = new AtomicInteger(count);
        try {
            for (int band = 0; band < count; band++) {
                int from = height * band / count;
                int to = height * (band + 1) / count;
                executor.execute(() -> {
                    try {
                        painter.paint(params, target, from, to);
                    } catch (RuntimeException ignored) {
                        Arrays.fill(target.pixels, from * target.width, to * target.width, 0);
                    } finally {
                        if (left.decrementAndGet() == 0) {
                            complete(params, target);
                        }
                    }
                });
            }
        } catch (RejectedExecutionException exception) {
            busy = false;
        }
    }

    private void complete(P params, Frame<P> frame) {
        try {
            if (finisher != null) {
                finisher.finish(params, frame);
            }
        } catch (RuntimeException ignored) {
            frame.params = params;
        }
        synchronized (this) {
            if (ready != null) {
                spare = ready;
            }
            ready = frame;
            busy = false;
            if (pending != null) {
                start();
            }
        }
    }

    public synchronized Frame<P> take() {
        if (ready != null) {
            if (shown != null) {
                spare = shown;
            }
            shown = ready;
            ready = null;
        }
        return shown;
    }

    public synchronized void clear() {
        ready = null;
        shown = null;
        spare = null;
        pending = null;
    }
}
