package dev.by1337.baf.world;

import dev.by1337.baf.util.ByWorldStorage;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Function;

public class WorldDataContainer<T extends Closeable> implements Listener {
    private final ByWorldStorage<T> storage;
    private final Function<World, T> factory;

    public WorldDataContainer(Plugin plugin, Function<World, T> factory) {
        this.factory = factory;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        storage = new ByWorldStorage<>(factory);
    }

    public void onUnload(WorldUnloadEvent e) {
        var v = storage.remove(e.getWorld());
        if (v != null) {
            try {
                v.close();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public T getIn(World w) {
        return storage.get(w);
    }

    public void close() {
        HandlerList.unregisterAll(this);
        storage.values().forEach(v -> {
            try {
                v.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        storage.clear();
    }

}
