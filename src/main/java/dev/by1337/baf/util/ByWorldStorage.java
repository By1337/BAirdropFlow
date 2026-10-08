package dev.by1337.baf.util;

import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class ByWorldStorage<T> {
    private final Map<World, T> storage = new HashMap<>();
    private final Function<World, T> factory;

    public ByWorldStorage(Function<World, T> factory) {
        this.factory = factory;
    }

    public @Nullable T getIfHas(World world) {
        return storage.computeIfAbsent(world, factory);
    }

    public T get(World world) {
        return storage.computeIfAbsent(world, factory);
    }

    public T remove(World world) {
        return storage.remove(world);
    }

    public Collection<T> values() {
        return storage.values();
    }

    public void clear() {
        storage.clear();
    }
}
