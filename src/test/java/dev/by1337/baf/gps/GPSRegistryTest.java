package dev.by1337.baf.gps;

import dev.by1337.core.BCore;
import dev.by1337.core.bridge.location.LocationBar;
import dev.by1337.plc.Placeholders;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GPSRegistryTest {
    private GPSRegistry registry(Server server) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        return new GPSRegistry(plugin);
    }

    @Test
    void entriesHaveUniqueIdsMatchingColorsAndDynamicLines() {
        World world = mock(World.class);
        Location location = new Location(world, 1, 64, 2);
        var placeholders = Placeholders.<Void>create().of("time", "10");
        var first = new GPSRegistry.GPSEntry(placeholders, "{gps_id} {color} {time}", location);
        var second = new GPSRegistry.GPSEntry(placeholders, "", location);
        assertNotEquals(first.id(), second.id());
        assertNotEquals(first.waypoint().getRgb(), second.waypoint().getRgb());
        assertEquals(first.id() + " " + String.format(Locale.ROOT, "#%06x", first.waypoint().getRgb())
                + " 10", first.getLine());
        placeholders.of("time", "9");
        assertTrue(first.getLine().endsWith(" 9"));
        location.setX(100);
        assertEquals(1, first.location().getX());
        assertEquals(1, first.waypoint().getX());
    }

    @Test
    void removalAndCloseRemoveVisibleWaypointsAndClearEntries() {
        Server server = mock(Server.class);
        World world = mock(World.class);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getWorld()).thenReturn(world);
        when(server.getPlayer(uuid)).thenReturn(player);
        LocationBar bar = mock(LocationBar.class);
        try (var core = mockStatic(BCore.class)) {
            core.when(BCore::locationBar).thenReturn(bar);
            GPSRegistry registry = registry(server);
            var first = registry.publish(new GPSRegistry.GPSEntry(Placeholders.create(), "", new Location(world, 1, 2, 3)));
            var second = registry.publish(new GPSRegistry.GPSEntry(Placeholders.create(), "", new Location(world, 4, 5, 6)));
            List<GPSRegistry.GPSEntry> snapshot = registry.getEntries();
            assertEquals(List.of(first, second), snapshot);
            assertTrue(registry.addWaypoint(player, first.id()));
            assertTrue(registry.addWaypoint(player, second.id()));
            verify(bar).send(player, first.waypoint());
            registry.remove(first.id());
            verify(bar).remove(player, first.waypoint());
            assertEquals(List.of(second), registry.getEntries());
            assertEquals(2, snapshot.size());
            registry.close();
            verify(bar).remove(player, second.waypoint());
            assertTrue(registry.getEntries().isEmpty());
            registry.close();
            verify(bar, times(1)).remove(player, second.waypoint());
        }
    }

    @Test
    void entriesWithoutLocationsCannotBeShownAndQuitDropsViewerState() {
        Server server = mock(Server.class);
        World world = mock(World.class);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getWorld()).thenReturn(world);
        when(server.getPlayer(uuid)).thenReturn(player);
        LocationBar bar = mock(LocationBar.class);
        try (var core = mockStatic(BCore.class)) {
            core.when(BCore::locationBar).thenReturn(bar);
            GPSRegistry registry = registry(server);
            var text = registry.publish(new GPSRegistry.GPSEntry(Placeholders.create(), "text", null));
            assertFalse(registry.addWaypoint(player, text.id()));
            assertFalse(registry.addWaypoint(player, -1));
            var point = registry.publish(new GPSRegistry.GPSEntry(Placeholders.create(), "", new Location(world, 1, 2, 3)));
            assertTrue(registry.addWaypoint(player, point.id()));
            registry.removeWaypoint(player, point.id());
            verify(bar).remove(player, point.waypoint());
            assertTrue(registry.addWaypoint(player, point.id()));
            registry.onQuit(new PlayerQuitEvent(player, ""));
            registry.close();
            verify(bar, times(1)).remove(player, point.waypoint());
        }
    }
}
