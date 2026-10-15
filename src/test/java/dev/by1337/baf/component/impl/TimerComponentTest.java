package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.edsl.context.EventContext;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerComponentTest {
    @Test
    void secondsStartAfterTwentyTicksAndCancellationStopsCallbacks() {
        MockBukkit.mock();
        try (var ctx = new EventContext(false)) {
            var air = new Airdrop(null, null, new Location(MockBukkit.getMock().addSimpleWorld("world"), 0, 64, 0));
            var ended = new AtomicInteger();
            var config = new TimerComponent.Config(2, ComponentFactory.EMPTY_HANDLER, ComponentFactory.EMPTY_HANDLER,
                    ComponentFactory.EMPTY_HANDLER, c -> {
                ended.incrementAndGet();
                return true;
            });
            var timer = (TimerComponent) config.create(air, "timer");
            air.components().add(timer, ctx);
            for (int i = 0; i < 19; i++) timer.onSyncTick(ctx);
            assertEquals(2, timer.getPlaceholder("timer"));
            timer.onSyncTick(ctx);
            assertEquals(1, timer.getPlaceholder("timer"));
            for (int i = 0; i < 19; i++) timer.onSyncTick(ctx);
            assertEquals(0, ended.get());
            timer.onSyncTick(ctx);
            assertEquals(1, ended.get());
            assertTrue(timer.isRemoved());
            for (int i = 0; i < 40; i++) timer.onSyncTick(ctx);
            assertEquals(1, ended.get());
            var cancelled = (TimerComponent) config.create(air, "cancelled");
            air.components().add(cancelled, ctx);
            cancelled.markRemoved(ctx);
            for (int i = 0; i < 40; i++) cancelled.onSyncTick(ctx);
            assertEquals(1, ended.get());
        } finally {
            MockBukkit.unmock();
        }
    }
}
