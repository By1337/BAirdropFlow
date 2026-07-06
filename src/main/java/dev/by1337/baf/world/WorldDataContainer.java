package dev.by1337.baf.world;

import dev.by1337.baf.util.ByWorldStorage;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

public class WorldDataContainer implements Listener {
    private final Plugin plugin;
    private final ByWorldStorage<WorldData> storage;

    public WorldDataContainer(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        storage = new ByWorldStorage<>(w -> new WorldData(w, plugin));
    }

    public void onUnload(WorldUnloadEvent e) {
        var v = storage.remove(e.getWorld());
        if (v != null) v.close();
    }

    public WorldData getWorldData(World w) {
        return storage.get(w);
    }

    public void close() {
        HandlerList.unregisterAll(this);
        storage.values().forEach(WorldData::close);
        storage.clear();
    }

}
