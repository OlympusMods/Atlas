package com.github.olympus.atlas.core.map;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public final class Names {
    private final ConcurrentHashMap<String, Integer> ids = new ConcurrentHashMap<>();
    private volatile String[] names = {""};
    private final int limit;

    public Names(int limit) {
        this.limit = limit;
        ids.put("", 0);
    }

    public int id(String name) {
        Integer known = ids.get(name);
        if (known != null) {
            return known;
        }
        synchronized (this) {
            known = ids.get(name);
            if (known != null) {
                return known;
            }
            String[] current = names;
            if (current.length >= limit) {
                return 0;
            }
            String[] grown = Arrays.copyOf(current, current.length + 1);
            grown[current.length] = name;
            names = grown;
            ids.put(name, current.length);
            return current.length;
        }
    }

    public int find(String name) {
        Integer known = ids.get(name);
        return known == null ? -1 : known;
    }

    public String name(int id) {
        String[] current = names;
        return id >= 0 && id < current.length ? current[id] : "";
    }

    public int size() {
        return names.length;
    }
}
