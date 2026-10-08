package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.baf.config.value.IntValue;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

/**
 * A cancellable deadline: removing it never fires on_timer_end.
 */
public class TimeoutComponent extends AbstractComponent implements TimerSystem.Tickable {
    private final Config config;
    private final Airdrop airdrop;
    private int remainingTicks;
    private boolean finished;

    private TimeoutComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.airdrop = airdrop;
        this.config = config;
    }

    @Override
    public void onCreate(EventContext ctx) {
        remainingTicks = config.ticks.getInt(airdrop);
        try (var ignored = ctx.tracer().enter(name + " on_create {", "}")) {
            config.onCreate.handle(ctx);
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_remove {", "}")) {
            config.onRemove.handle(ctx);
        }
    }

    @Override
    public void onSyncTick(EventContext ctx) {
        // A removed component can still appear in TimerSystem's current iteration snapshot.
        if (isRemoved() || finished || --remainingTicks > 0) return;
        finished = true;
        try (var ignored = ctx.tracer().enter(name + " on_timer_end {", "}")) {
            config.onTimerEnd.handle(ctx);
        } finally {
            // The output may already have removed this node or destroyed the entire airdrop.
            if (!isRemoved()) markRemoved(ctx);
        }
    }

    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Таймаут", "Ожидает заданное число тиков. remove отменяет ожидание без on_timer_end. По истечении времени: on_timer_end, затем on_remove.")
                .integer("ticks", "Время ожидания, тиков", 1200, 1, false)
                .events("on_create", "on_remove", "on_timer_end")
                .inputs("create", "remove");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                IntValue.DECODER.fieldOf("ticks", new IntValue(1200)),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER),
                HANDLER.fieldOf("on_timer_end", EMPTY_HANDLER)
        );

        private final IntValue ticks;
        private final EventHandler onCreate;
        private final EventHandler onRemove;
        private final EventHandler onTimerEnd;

        public Config(int ticks, EventHandler onRemove, EventHandler onTimerEnd) {
            this(new IntValue(ticks), EMPTY_HANDLER, onRemove, onTimerEnd);
        }

        public Config(int ticks, EventHandler onCreate, EventHandler onRemove, EventHandler onTimerEnd) {
            this(new IntValue(ticks), onCreate, onRemove, onTimerEnd);
        }

        public Config(IntValue ticks, EventHandler onCreate, EventHandler onRemove, EventHandler onTimerEnd) {
            this.ticks = ticks.checked(v -> v > 0, "Timeout ticks must be positive");
            this.onCreate = onCreate;
            this.onRemove = onRemove;
            this.onTimerEnd = onTimerEnd;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new TimeoutComponent(this, name, airdrop);
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
