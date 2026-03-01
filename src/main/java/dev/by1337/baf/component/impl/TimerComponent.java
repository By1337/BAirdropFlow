package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.command.AirdropContext;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.component.ComponentHolder;
import dev.by1337.baf.handler.ComponentHandler;
import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;

public class TimerComponent extends AbstractComponent {

    private final Config config;
    private int timer;

    private TimerComponent(Config config, String name, ComponentHolder owner) {
        super(name, owner);
        timer = config.timer;
        this.config = config;
        exportPlaceholder("timer", () -> timer);
    }

    @Override
    public void onCreate(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_create {", "}")){
            config.on_create.handle(ctx);
        }
    }

    @Override
    public void onRemove(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_remove {", "}")){
            config.on_remove.handle(ctx);
        }
    }
    public void onTimerEnd(AirdropContext ctx){
        try (var ignored2 = ctx.tracer().enter(name + " on_timer_end {", "}")){
            config.on_timer_end.handle(ctx);
            markRemoved(ctx);
        }
    }

    public void onTick(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_tick {", "}")){
            if (--timer == 0) {
                onTimerEnd(ctx);
            } else {
                config.on_tick.handle(ctx);
            }
        }
    }

    public static class Config implements ComponentFactory {
        public static final YamlCodec<Config> CODEC = RecordYamlCodecBuilder.mapOf(
                Config::new,
                YamlCodec.INT.fieldOf("timer", v -> v.timer),
                ComponentHandler.CODEC.fieldOf("on_create", v -> v.on_create, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_tick", v -> v.on_tick, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_remove", v -> v.on_remove, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_timer_end", v -> v.on_timer_end, ComponentHandler.EMPTY)
        );
        private final int timer;
        private final ComponentHandler on_create;
        private final ComponentHandler on_tick;
        private final ComponentHandler on_remove;
        private final ComponentHandler on_timer_end;

        public Config(int timer, ComponentHandler on_create, ComponentHandler on_tick, ComponentHandler remove, ComponentHandler onTimerEnd) {
            this.timer = timer;
            this.on_create = on_create;
            this.on_tick = on_tick;
            this.on_remove = remove;
            on_timer_end = onTimerEnd;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new TimerComponent(this, name, airdrop.components());
        }

        @Override
        public YamlCodec<? extends ComponentFactory> codec() {
            return CODEC;
        }
    }
}
