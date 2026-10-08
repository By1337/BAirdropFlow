package dev.by1337.baf.interact;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;

import java.util.*;
import java.util.function.Consumer;

public class InteractManager {
    private final WorldStorage<InteractListener<BlockBreakEvent>> blockBreakListeners = new WorldStorage<>();
    private final WorldStorage<InteractListener<PlayerInteractEvent>> blockClickListeners = new WorldStorage<>();
    private final Plugin plugin;
    private final WorldListener listener;

    public InteractManager(Plugin plugin) {
        this.plugin = plugin;
        listener = new WorldListener();
    }

    @ApiStatus.Internal
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @ApiStatus.Internal
    public void disable() {
        HandlerList.unregisterAll(listener);
        blockBreakListeners.clear();
        blockClickListeners.clear();
    }

    public InteractListener<BlockBreakEvent> registerBlockBreak(World world, int x, int y, int z, Consumer<BlockBreakEvent> c) {
        InteractListener<BlockBreakEvent> listener = new InteractListener<>() {
            final String w = world.getName();

            @Override
            public void on(BlockBreakEvent event) {
                c.accept(event);
            }

            @Override
            public void unregister() {
                blockBreakListeners.remove(w, x, y, z, this);
            }
        };
        blockBreakListeners.put(world.getName(), x, y, z, listener);
        return listener;
    }

    public InteractListener<PlayerInteractEvent> registerBlockClick(World world, int x, int y, int z, Consumer<PlayerInteractEvent> c) {
        InteractListener<PlayerInteractEvent> listener = new InteractListener<>() {
            final String w = world.getName();

            @Override
            public void on(PlayerInteractEvent event) {
                c.accept(event);
            }

            @Override
            public void unregister() {
                blockClickListeners.remove(w, x, y, z, this);
            }
        };
        blockClickListeners.put(world.getName(), x, y, z, listener);
        return listener;
    }

    public class WorldListener implements Listener {

        @EventHandler
        public void onBlockBreak(BlockBreakEvent event) {
            var block = event.getBlock();
            var set = blockBreakListeners.get(
                    block.getWorld().getName(),
                    block.getX(),
                    block.getY(),
                    block.getZ()
            );
            if (set == null) return;
            if (!set.isEmpty()) {
                new ArrayList<>(set).forEach(listener -> listener.on(event));
            }
        }

        @EventHandler
        public void onBlockInteract(PlayerInteractEvent event) {
            var block = event.getClickedBlock();
            if (block == null) return;
            var set = blockClickListeners.get(
                    block.getWorld().getName(),
                    block.getX(),
                    block.getY(),
                    block.getZ()
            );
            if (set == null) return;
            if (!set.isEmpty()) {
                new ArrayList<>(set).forEach(listener -> listener.on(event));
            }
        }
    }

    private static class WorldStorage<T> {
        private final Map<String, Long2ObjectOpenHashMap<Set<T>>> storage = new HashMap<>();

        public void remove(String world, int x, int y, int z, T t) {
            var map = storage.get(world);
            if (map == null) return;
            var set = map.get(asLong(x, y, z));
            if (set == null) return;
            set.remove(t);
        }

        public void put(String world, int x, int y, int z, T t) {
            var map = storage.computeIfAbsent(world, k -> new Long2ObjectOpenHashMap<>());
            long key = asLong(x, y, z);
            var set = map.get(key);
            if (set == null) {
                set = Collections.newSetFromMap(new IdentityHashMap<>());
                map.put(key, set);
            }
            set.add(t);
        }

        public Set<T> get(String world, int x, int y, int z) {
            var map = storage.get(world);
            if (map == null) return null;
            return map.get(asLong(x, y, z));
        }

        public void clear() {
            storage.clear();
        }

        public static long asLong(int x, int y, int z) {
            return ((long) x & 67108863L) << 38 | (long) y & 4095L | ((long) z & 67108863L) << 12;
        }
    }
}
