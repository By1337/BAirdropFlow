package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.BAF;
import dev.by1337.baf.gps.GPSRegistry;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.YamlValue;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GPSComponentTest {
    @Test
    void publishesDynamicEntryAndCleansUpOnReplacementRemovalAndAirdropCleanup() {
        Server server = mock(Server.class);
        Plugin plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Airdrop airdrop = new Airdrop(null, null, new Location(world, 10, 64, 20));
        var decoded = GPSComponent.Config.DECODER.decode(YamlValue.wrap(Map.of(
                "gps_line", "{world} {x} {args:status} {gps_id} {color}")));
        assertFalse(decoded.hasError(), decoded.error());
        try (var registry = new GPSRegistry(plugin);
             var baf = mockStatic(BAF.class);
             var ctx = new EventContext(false)) {
            baf.when(BAF::gps).thenReturn(registry);
            EventHandler onCreate = mock(EventHandler.class);
            EventHandler onRemove = mock(EventHandler.class);
            doAnswer(invocation -> {
                assertEquals(1, registry.getEntries().size());
                return null;
            }).when(onCreate).handle(ctx);
            doAnswer(invocation -> {
                assertTrue(registry.getEntries().isEmpty());
                return null;
            }).when(onRemove).handle(ctx);
            var config = new GPSComponent.Config(decoded.result().publish_location(),
                    decoded.result().gps_line(), onCreate, onRemove);
            var component = config.create(airdrop, "gps");
            airdrop.args().put("status", "waiting");
            component.onCreate(ctx);
            assertEquals(1, registry.getEntries().size());
            var first = registry.getEntries().getFirst();
            assertEquals(airdrop.location(), first.location());
            assertNotNull(first.waypoint());
            assertTrue(first.getLine().startsWith("world 10 waiting " + first.id() + " #"));
            airdrop.args().put("status", "open");
            assertTrue(first.getLine().startsWith("world 10 open "));
            component.onCreate(ctx);
            assertEquals(1, registry.getEntries().size());
            assertNotEquals(first.id(), registry.getEntries().getFirst().id());
            component.onRemove(ctx);
            assertTrue(registry.getEntries().isEmpty());
            component.onCreate(ctx);
            airdrop.cleaner().cleanup();
            assertTrue(registry.getEntries().isEmpty());
            component.onRemove(ctx);
            verify(onCreate, times(3)).handle(ctx);
            verify(onRemove, times(2)).handle(ctx);
        }
    }

    @Test
    void canPublishTextWithoutWaypointAndSchemaHasStandardInputsAndOutputs() {
        Server server = mock(Server.class);
        Plugin plugin = mock(Plugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        Airdrop airdrop = new Airdrop(null, null, new Location(mock(World.class), 1, 2, 3));
        var decoded = GPSComponent.Config.DECODER.decode(YamlValue.wrap(Map.of(
                "publish_location", false, "gps_line", "text")));
        assertFalse(decoded.hasError(), decoded.error());
        try (var registry = new GPSRegistry(plugin);
             var baf = mockStatic(BAF.class);
             var ctx = new EventContext(false)) {
            baf.when(BAF::gps).thenReturn(registry);
            var component = decoded.result().create(airdrop, "gps");
            component.onCreate(ctx);
            var entry = registry.getEntries().getFirst();
            assertEquals("text", entry.getLine());
            assertNull(entry.location());
            assertNull(entry.waypoint());
            airdrop.cleaner().cleanup();
            assertTrue(registry.getEntries().isEmpty());
        }
        var schema = GPSComponent.Config.WEB_SCHEMA.json("default:gps");
        assertEquals("[\"create\",\"remove\"]", schema.getAsJsonArray("x-inputs").toString());
        assertEquals("[\"on_create\",\"on_remove\"]", schema.getAsJsonArray("x-events").toString());
        assertTrue(schema.getAsJsonObject("properties").has("on_create"));
        assertTrue(schema.getAsJsonObject("properties").has("on_remove"));
    }
}
