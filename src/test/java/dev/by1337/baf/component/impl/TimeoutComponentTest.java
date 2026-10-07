package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TimeoutComponentTest {
    private Airdrop airdrop;
    private EventContext ctx;
    private final List<String> events = new ArrayList<>();

    @BeforeAll
    static void initializeContextWithoutPlaceholderApi() {
        try (var ignored = new EventContext(false)) {}
    }

    @BeforeEach
    void setup() {
        var server = MockBukkit.mock();
        airdrop = new Airdrop(null, null, new Location(server.addSimpleWorld("world"), 0, 64, 0));
        ctx = new EventContext(false, null, Airdrop.PLACEHOLDERS.bindCtx(airdrop));
    }

    @AfterEach
    void cleanup() {
        if (ctx != null) ctx.close();
        MockBukkit.unmock();
    }

    private TimeoutComponent create(int ticks, EventHandler onTimerEnd) {
        var config = new TimeoutComponent.Config(ticks, ignored -> events.add("remove"), onTimerEnd);
        var component = (TimeoutComponent) config.create(airdrop, "ttl");
        airdrop.components().add(component, ctx);
        return component;
    }

    @Test
    void creationRunsOnCreateBeforeTheTimeoutStartsTicking() {
        var config = new TimeoutComponent.Config(1,
                ignored -> events.add("create"),
                ignored -> events.add("remove"),
                ignored -> events.add("end"));
        var component = (TimeoutComponent) config.create(airdrop, "ttl");
        airdrop.components().add(component, ctx);
        assertEquals(List.of("create"), events);
        component.onSyncTick(ctx);
        assertEquals(List.of("create", "end", "remove"), events);
    }

    @Test
    void expiresOnExactlyTheConfiguredTickAndFiresEachOutputOnce() {
        var component = create(3, ignored -> events.add("end"));
        assertTrue(events.isEmpty());
        component.onSyncTick(ctx);
        component.onSyncTick(ctx);
        assertTrue(events.isEmpty());
        assertSame(component, airdrop.components().get("ttl"));
        component.onSyncTick(ctx);
        assertEquals(List.of("end", "remove"), events);
        assertTrue(component.isRemoved());
        assertNull(airdrop.components().get("ttl"));
        component.onSyncTick(ctx);
        assertEquals(List.of("end", "remove"), events);
    }

    @Test
    void cancellationBeforeTheDeadlineNeverFiresTimerEndEvenInTheCurrentTickSnapshot() {
        var component = create(2, ignored -> events.add("end"));
        component.onSyncTick(ctx);
        component.markRemoved(ctx); // A click cancels TTL on its last tick.
        for (int i = 0; i < 5; i++) component.onSyncTick(ctx);
        assertEquals(List.of("remove"), events);
        assertNull(airdrop.components().get("ttl"));

        // Recreating a cancelled timeout with the same ID starts a fresh countdown.
        var replacement = create(1, ignored -> events.add("end"));
        component.onSyncTick(ctx);
        assertSame(replacement, airdrop.components().get("ttl"));
        replacement.onSyncTick(ctx);
        assertEquals(List.of("remove", "end", "remove"), events);
    }

    @Test
    void completionHandlerCanRemoveTheNodeAndReenterTickWithoutDuplicateOutputs() {
        var component = create(1, context -> {
            events.add("end");
            var current = (TimeoutComponent) airdrop.components().get("ttl");
            current.onSyncTick(context);
            current.markRemoved(context);
            return true;
        });
        component.onSyncTick(ctx);
        assertEquals(List.of("end", "remove"), events);
        assertTrue(component.isRemoved());
    }

    @Test
    void failingCompletionHandlerStillRemovesTheNode() {
        var component = create(1, ignored -> { throw new IllegalStateException("handler failed"); });
        assertThrows(IllegalStateException.class, () -> component.onSyncTick(ctx));
        assertTrue(component.isRemoved());
        assertNull(airdrop.components().get("ttl"));
        component.onSyncTick(ctx);
        assertEquals(List.of("remove"), events);
    }

    @Test
    void decoderAcceptsDefaultsAndRejectsNonPositiveDurations() {
        var defaults = TimeoutComponent.Config.DECODER.decode(YamlValue.wrap(Map.of()));
        assertFalse(defaults.hasError(), defaults.error());
        assertNotNull(defaults.result());
        for (int ticks : new int[]{0, -1, -100}) {
            assertTrue(TimeoutComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("ticks", ticks))).hasError());
        }
    }
}
