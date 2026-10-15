package dev.by1337.baf.component.impl.loot;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.AirdropManager;
import dev.by1337.baf.config.value.IntValue;
import dev.by1337.baf.loot.LootTable;
import dev.by1337.baf.loot.LootTables;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.YamlValue;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpreadingComponentTest {
    private Airdrop airdrop;
    private EventContext ctx;
    private final List<String> events = new ArrayList<>();

    @BeforeAll
    static void initContext() {
        try (var ignored = new EventContext(false)) {
        }
    }

    @BeforeEach
    void setup() {
        var server = MockBukkit.mock();
        var manager = mock(AirdropManager.class);
        when(manager.plugin()).thenReturn(MockBukkit.createMockPlugin());
        when(manager.lootTables()).thenReturn(new LootTables(Map.of("test", new LootTable(List.of()))));
        airdrop = new Airdrop(null, manager, new Location(server.addSimpleWorld("world"), 0, 64, 0));
        ctx = new EventContext(false);
    }

    @AfterEach
    void cleanup() {
        airdrop.cleaner().cleanup();
        ctx.close();
        MockBukkit.unmock();
    }

    private SpreadingComponent create(EventHandler end, EventHandler remove) {
        var c = SpreadingComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("loot_table", "test"))).getOrThrow();
        var config = new SpreadingComponent.Config(c.lootTable(), new IntValue("{args:rolls}"), c.offsets(),
                c.intervalTicks(), c.horizontalSpeed(), c.verticalSpeed(), c.pickupDelayTicks(),
                c.itemLifetimeTicks(), c.obfuscationMaterials(), c.obfuscationName(), c.trailAnimation(),
                c.trailIntervalTicks(), ignored -> events.add("create"), end, remove);
        airdrop.args().put("rolls", "0");
        var component = (SpreadingComponent) config.create(airdrop, "spread");
        airdrop.components().add(component, ctx);
        return component;
    }

    @Test
    void emptyLootCompletesOnceAndResolvesParametersThroughAirdrop() {
        var component = create(ignored -> events.add("end"), ignored -> events.add("remove"));
        assertEquals(List.of("create"), events);
        component.onSyncTick(ctx);
        component.onSyncTick(ctx);
        assertEquals(List.of("create", "end", "remove"), events);
        assertTrue(component.isRemoved());
        assertNull(airdrop.components().get("spread"));
    }

    @Test
    void earlyRemovalNeverFiresCompletion() {
        var component = create(ignored -> events.add("end"), ignored -> events.add("remove"));
        component.markRemoved(ctx);
        component.onSyncTick(ctx);
        assertEquals(List.of("create", "remove"), events);
    }

    @Test
    void reentrantCompletionAndRemovalFireOnlyOnce() {
        var component = create(context -> {
            events.add("end");
            var current = (SpreadingComponent) airdrop.components().get("spread");
            current.onSyncTick(context);
            return true;
        }, ignored -> events.add("remove"));
        component.onSyncTick(ctx);
        assertEquals(List.of("create", "end", "remove"), events);
    }

    @Test
    void failingRemovalStillReleasesTheListener() {
        var plugin = airdrop.manager().plugin();
        var component = create(ignored -> true, ignored -> {throw new IllegalStateException("failed");});
        assertFalse(org.bukkit.event.HandlerList.getRegisteredListeners(plugin).isEmpty());
        assertThrows(IllegalStateException.class, () -> component.markRemoved(ctx));
        assertTrue(org.bukkit.event.HandlerList.getRegisteredListeners(plugin).isEmpty());
    }

    @Test
    void decoderRejectsBadTimingVelocityAndMaskMaterials() {
        for (var entry : Map.<String, Object>of("rolls", -1, "interval_ticks", 0,
                "horizontal_speed", -0.1, "vertical_speed", "NaN", "pickup_delay_ticks", -1,
                "item_lifetime_ticks", 0, "trail_interval_ticks", 0,
                "obfuscation_materials", List.of("AIR")).entrySet()) {
            var values = new HashMap<String, Object>();
            values.put("loot_table", "test");
            values.put(entry.getKey(), entry.getValue());
            assertTrue(SpreadingComponent.Config.DECODER.decode(YamlValue.wrap(values)).hasError(), entry.getKey());
        }
    }
}
