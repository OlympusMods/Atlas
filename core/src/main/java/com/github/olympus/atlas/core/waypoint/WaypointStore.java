package com.github.olympus.atlas.core.waypoint;

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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WaypointStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("atlas");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final class Document {
        public int version = 1;
        public List<Waypoint> waypoints = new ArrayList<>();
        public List<String> hiddenGroups = new ArrayList<>();
    }

    private final Path file;
    private final List<Waypoint> waypoints = new ArrayList<>();
    private final TreeSet<String> hiddenGroups = new TreeSet<>();
    private boolean dirty;
    private int revision;

    public WaypointStore(Path file) {
        this.file = file;
        if (file != null && Files.isRegularFile(file)) {
            Document document = read(file);
            if (document != null) {
                adopt(document);
            }
        }
    }

    public static Document read(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Document document = GSON.fromJson(reader, Document.class);
            if (document == null) {
                return null;
            }
            if (document.waypoints == null) {
                document.waypoints = new ArrayList<>();
            }
            if (document.hiddenGroups == null) {
                document.hiddenGroups = new ArrayList<>();
            }
            document.waypoints.removeIf(waypoint -> waypoint == null);
            document.waypoints.forEach(Waypoint::sanitize);
            return document;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not read {}: {}", path, exception.getMessage());
            return null;
        }
    }

    private void adopt(Document document) {
        waypoints.addAll(document.waypoints);
        hiddenGroups.addAll(document.hiddenGroups);
    }

    public synchronized List<Waypoint> all() {
        return new ArrayList<>(waypoints);
    }

    public synchronized int revision() {
        return revision;
    }

    public synchronized List<Waypoint> in(String dimension) {
        List<Waypoint> list = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            if (waypoint.dimension.equals(dimension)) {
                list.add(waypoint);
            }
        }
        return list;
    }

    public synchronized boolean shown(Waypoint waypoint) {
        return waypoint.visible && !hiddenGroups.contains(waypoint.group);
    }

    public synchronized List<String> groups() {
        TreeSet<String> groups = new TreeSet<>();
        for (Waypoint waypoint : waypoints) {
            groups.add(waypoint.group);
        }
        return new ArrayList<>(groups);
    }

    public synchronized boolean groupHidden(String group) {
        return hiddenGroups.contains(group);
    }

    public synchronized void hideGroup(String group, boolean hidden) {
        if (hidden ? hiddenGroups.add(group) : hiddenGroups.remove(group)) {
            touch();
        }
    }

    public synchronized void add(Waypoint waypoint) {
        waypoints.add(waypoint.sanitize());
        touch();
    }

    public synchronized void update(Waypoint waypoint) {
        for (int i = 0; i < waypoints.size(); i++) {
            if (waypoints.get(i).id.equals(waypoint.id)) {
                waypoints.set(i, waypoint.sanitize());
                touch();
                return;
            }
        }
        add(waypoint);
    }

    public synchronized boolean remove(String id) {
        if (waypoints.removeIf(waypoint -> waypoint.id.equals(id))) {
            touch();
            return true;
        }
        return false;
    }

    public synchronized void touch() {
        dirty = true;
        revision++;
    }

    public synchronized int trimDeaths(int limit, long expiryMillis, long now) {
        List<Waypoint> deaths = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            if (waypoint.kind == Waypoint.Kind.DEATH) {
                deaths.add(waypoint);
            }
        }
        deaths.sort(Comparator.comparingLong((Waypoint waypoint) -> waypoint.created).reversed());
        int removed = 0;
        for (int i = 0; i < deaths.size(); i++) {
            Waypoint death = deaths.get(i);
            if (i >= limit || (expiryMillis > 0 && now - death.created > expiryMillis)) {
                waypoints.remove(death);
                removed++;
            }
        }
        if (removed > 0) {
            touch();
        }
        return removed;
    }

    public synchronized int trimTemporary(long expiryMillis, long now, String dimension, double px, double py,
            double pz, boolean onArrival) {
        int before = waypoints.size();
        waypoints.removeIf(waypoint -> waypoint.kind == Waypoint.Kind.TEMPORARY
                && ((expiryMillis > 0 && now - waypoint.created > expiryMillis)
                        || (onArrival && waypoint.dimension.equals(dimension)
                                && Math.hypot(waypoint.x + 0.5 - px, waypoint.z + 0.5 - pz) < 4.0
                                && Math.abs(waypoint.y - py) < 12.0)));
        int removed = before - waypoints.size();
        if (removed > 0) {
            touch();
        }
        return removed;
    }

    public synchronized int merge(List<Waypoint> incoming) {
        int added = 0;
        for (Waypoint candidate : incoming) {
            candidate.sanitize();
            boolean known = false;
            for (Waypoint waypoint : waypoints) {
                if (waypoint.id.equals(candidate.id) || (waypoint.x == candidate.x && waypoint.y == candidate.y
                        && waypoint.z == candidate.z && waypoint.dimension.equals(candidate.dimension)
                        && waypoint.name.equals(candidate.name))) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                waypoints.add(candidate);
                added++;
            }
        }
        if (added > 0) {
            touch();
        }
        return added;
    }

    public synchronized List<Waypoint> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Waypoint> found = new ArrayList<>();
        for (Waypoint waypoint : waypoints) {
            if (needle.isEmpty() || waypoint.name.toLowerCase(Locale.ROOT).contains(needle)
                    || waypoint.group.toLowerCase(Locale.ROOT).contains(needle)) {
                found.add(waypoint);
            }
        }
        return found;
    }

    public synchronized Document document() {
        Document document = new Document();
        for (Waypoint waypoint : waypoints) {
            document.waypoints.add(waypoint.copy());
        }
        document.hiddenGroups.addAll(hiddenGroups);
        return document;
    }

    public boolean saveIfDirty() {
        Document document;
        synchronized (this) {
            if (!dirty || file == null) {
                return false;
            }
            dirty = false;
            document = document();
        }
        return write(file, document);
    }

    public static boolean write(Path path, Document document) {
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(document, writer);
            }
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Could not save {}: {}", path, exception.getMessage());
            return false;
        }
    }
}
