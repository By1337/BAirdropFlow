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

public class TimerComponent extends AbstractComponent implements TimerSystem.Tickable {

    private final Config config;
    private final Airdrop airdrop;
    private int timer;
    private int tick = 0;

    private TimerComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.airdrop = airdrop;
        this.config = config;
        exportPlaceholder("timer", () -> timer);
    }

    @Override
    public void onCreate(EventContext ctx) {
        timer = config.timer.getInt(airdrop);
        tick = 0;
        try (var ignored = ctx.tracer().enter(name + " on_create {", "}")) {
            config.on_create.handle(ctx);
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_remove {", "}")) {
            config.on_remove.handle(ctx);
        }
    }

    public void onTimerEnd(EventContext ctx) {
        try (var ignored2 = ctx.tracer().enter(name + " on_timer_end {", "}")) {
            config.on_timer_end.handle(ctx);
            markRemoved(ctx);
        }
    }

    public void onTick(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_tick {", "}")) {
            if (--timer == 0) {
                onTimerEnd(ctx);
            } else {
                config.on_tick.handle(ctx);
            }
        }
    }

    @Override
    public void onSyncTick(EventContext ctx) {
        if (!isRemoved() && ++tick % 20 == 0)
            onTick(ctx);
    }

    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Таймер", "Секундный таймер. Плейсхолдер {ctx:имя.timer}.")
                .integer("timer", "Время, секунд", 20, 1, false)
                .placeholder("timer", "Осталось секунд", "Текущее значение таймера.")
                .events("on_create", "on_tick", "on_timer_end", "on_remove")
                .inputs("create", "remove");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                IntValue.DECODER.fieldOf("timer", new IntValue(20)),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_tick", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER),
                HANDLER.fieldOf("on_timer_end", EMPTY_HANDLER)
        );
        private final IntValue timer;
        private final EventHandler on_create;
        private final EventHandler on_tick;
        private final EventHandler on_remove;
        private final EventHandler on_timer_end;

        public Config(int timer, EventHandler on_create, EventHandler on_tick, EventHandler remove, EventHandler onTimerEnd) {
            this(new IntValue(timer), on_create, on_tick, remove, onTimerEnd);
        }

        public Config(IntValue timer, EventHandler on_create, EventHandler on_tick, EventHandler remove, EventHandler onTimerEnd) {
            this.timer = timer.checked(v -> v > 0, "Timer must be positive");
            this.on_create = on_create;
            this.on_tick = on_tick;
            this.on_remove = remove;
            on_timer_end = onTimerEnd;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new TimerComponent(this, name, airdrop);
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
