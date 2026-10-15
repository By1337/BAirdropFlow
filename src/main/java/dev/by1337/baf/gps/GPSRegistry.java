package dev.by1337.baf.gps;

import dev.by1337.baf.BAF;
import dev.by1337.core.BCore;
import dev.by1337.core.bridge.location.LocationBar;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.plc.PlaceholderResolver;
import dev.by1337.plc.Placeholders;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class GPSRegistry implements Listener, Closeable {
    private final Plugin plugin;
    private final Map<Long, GPSEntry> entries = new LinkedHashMap<>();
    private final Map<UUID, Set<Long>> viewers = new HashMap<>();
    private final @Nullable LocationBar locationBar = BCore.locationBar();

    public GPSRegistry(Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public GPSEntry publish(GPSEntry entry) {
        entries.put(entry.id(), entry);
        return entry;
    }

    public List<GPSEntry> getEntries() {
        return List.copyOf(entries.values());
    }

    public void remove(long id) {
        GPSEntry entry = entries.remove(id);
        if (entry == null) return;
        var iterator = viewers.entrySet().iterator();
        while (iterator.hasNext()) {
            var viewer = iterator.next();
            if (viewer.getValue().remove(id)) {
                Player player = plugin.getServer().getPlayer(viewer.getKey());
                if (player != null && entry.waypoint() != null) {
                    if (locationBar != null)
                        locationBar.remove(player, entry.waypoint());
                }
            }
            if (viewer.getValue().isEmpty()) iterator.remove();
        }
    }

    public boolean addWaypoint(Player player, long id) {
        GPSEntry entry = entries.get(id);
        if (entry == null || entry.waypoint() == null) return false;
        var loc = entry.location;
        if (locationBar != null)
            locationBar.send(player, entry.waypoint());
        BAF.sendMessage("gps_waypoint_added", player, Placeholders.<EventContext>create()
                .of("x", Integer.toString(loc.getBlockX()))
                .of("y", Integer.toString(loc.getBlockY()))
                .of("z", Integer.toString(loc.getBlockZ()))
                .of("world", loc.getWorld().getName())
        );
        viewers.computeIfAbsent(player.getUniqueId(), key -> new HashSet<>()).add(id);
        return true;
    }

    public void removeWaypoint(Player player, long id) {
        Set<Long> ids = viewers.get(player.getUniqueId());
        if (ids == null || !ids.remove(id)) return;
        GPSEntry entry = entries.get(id);
        if (entry != null && entry.waypoint() != null) {
            if (locationBar != null)
                locationBar.remove(player, entry.waypoint());
        }
        if (ids.isEmpty()) viewers.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        clearWaypoints(event.getPlayer());
    }

    private void clearWaypoints(Player player) {
        Set<Long> ids = viewers.remove(player.getUniqueId());
        if (ids == null) return;
        for (long id : ids) {
            GPSEntry entry = entries.get(id);
            if (entry != null && entry.waypoint() != null) {
                if (locationBar != null)
                    locationBar.remove(player, entry.waypoint());
            }
        }
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        for (UUID uuid : List.copyOf(viewers.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) clearWaypoints(player);
        }
        viewers.clear();
        entries.clear();
    }


    public static class GPSEntry {
        private static final AtomicLong COUNTER = new AtomicLong();
        private static final int[] COLORS = {
                0xff4040, 0x40ff40, 0x4080ff, 0xffff40, 0xff40ff,
                0x40ffff, 0xffa040, 0xa040ff, 0x40ffa0, 0xff4080,
                0x80c0ff, 0xc0ff40, 0xff80c0, 0x40c0a0, 0xc08040,
                0x8040ff, 0xffc080, 0x80ff80, 0xc080ff, 0xffffff
        };
        private static final PlaceholderResolver<GPSEntry> PLACEHOLDERS = Placeholders.<GPSEntry>create()
                .of("gps_id", (key, v) -> v.id)
                .of("color", (key, v) -> String.format(Locale.ROOT, "#%06x", v.color));
        private final long id = COUNTER.getAndIncrement();
        private final int color = COLORS[Math.floorMod(id, COLORS.length)];

        private final PlaceholderResolver<Void> placeholders;
        private final String rawLine;
        private final @Nullable Location location;
        private final @Nullable LocationBar.Waypoint waypoint;

        public GPSEntry(PlaceholderResolver<Void> placeholders, String rawLine, @Nullable Location location) {
            this.placeholders = placeholders.and(PLACEHOLDERS.bindCtx(this));
            this.rawLine = rawLine;
            this.location = location == null ? null : location.clone();
            if (location != null) {
                waypoint = new LocationBar.Waypoint()
                        .setColor(color)
                        .setPos(location);
            } else {
                waypoint = null;
            }

        }

        public String getLine() {
            return placeholders.setPlaceholders(rawLine, null);
        }

        public long id() {
            return id;
        }

        public @Nullable Location location() {
            return location == null ? null : location.clone();
        }

        public @Nullable LocationBar.Waypoint waypoint() {
            return waypoint;
        }
    }

    public record GPSConfig(
            boolean publish,
            boolean publish_location,
            String gps_line
    ) {
        public static final GPSConfig EMPTY = new GPSConfig(false, false, "pupupu");
        public static final YamlDecoder<GPSConfig> DECODER = RecordYamlDecoder.mapOf(
                GPSConfig::new,
                YamlDecoder.BOOL.fieldOf("publish", false),
                YamlDecoder.BOOL.fieldOf("publish_location", false),
                YamlDecoder.STRING.fieldOf("gps_line", "")
        );
    }
}
