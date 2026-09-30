package dev.by1337.baf.world.fake;

import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;

import java.util.HashMap;
import java.util.Map;

public class BlockFlagger {
    private final Map<String, Long2BooleanOpenHashMap> storage = new HashMap<>();

    public void remove(String world, int x, int y, int z) {
        var map = storage.get(world);
        if (map == null) return;
        map.remove(asLong(x, y, z));
    }

    public void set(String world, int x, int y, int z, boolean flag) {
        if (!flag) {
            remove(world, x, y, z);
            return;
        }
        var map = storage.computeIfAbsent(world, k -> new Long2BooleanOpenHashMap());
        map.put(asLong(x, y, z), true);
    }

    public boolean is(String world, int x, int y, int z) {
        var map = storage.get(world);
        if (map == null) return false;
        return map.get(asLong(x, y, z));
    }

    public void clear() {
        storage.clear();
    }

    private static long asLong(int x, int y, int z) {
        return ((long) x & 67108863L) << 38 | (long) y & 4095L | ((long) z & 67108863L) << 12;
    }
}