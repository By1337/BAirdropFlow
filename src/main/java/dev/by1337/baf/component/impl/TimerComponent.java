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
        exportEvent("tick", this::onTick);
        exportPlaceholder("timer", () -> timer);
    }

    @Override
    public void onCreate(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " create {", "}")){
            config.create.handle(ctx);
        }
    }

    @Override
    public void onRemove(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " remove {", "}")){
            config.remove.handle(ctx);
        }
    }

    public void onTick(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " tick {", "}")){
            if (--timer <= 0) {
                markRemoved(ctx);
            } else {
                config.tick.handle(ctx);
            }
        }
    }

    public static class Config implements ComponentFactory {
        public static final YamlCodec<Config> CODEC = RecordYamlCodecBuilder.mapOf(
                Config::new,
                YamlCodec.INT.fieldOf("timer", v -> v.timer),
                ComponentHandler.CODEC.fieldOf("on_create", v -> v.create, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_tick", v -> v.tick, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_remove", v -> v.remove, ComponentHandler.EMPTY)
        );
        private final int timer;
        private final ComponentHandler create;
        private final ComponentHandler tick;
        private final ComponentHandler remove;

        public Config(int timer, ComponentHandler create, ComponentHandler tick, ComponentHandler remove) {
            this.timer = timer;
            this.create = create;
            this.tick = tick;
            this.remove = remove;
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
