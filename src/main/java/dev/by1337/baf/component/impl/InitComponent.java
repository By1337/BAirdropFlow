package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.component.ComponentHolder;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

public class InitComponent extends AbstractComponent {
    private final Config config;

    private InitComponent(Config config, String name, ComponentHolder owner) {
        super(name, owner);
        this.config = config;
    }

    public void onInit(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " init {", "}")) {
            config.init.handle(ctx);
            if (!isRemoved()) markRemoved(ctx);
        }
    }

    @Override
    public void onCreate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " create {", "}")) {
            onInit(ctx); //proxy
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
    }

    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Инициализация", "Входная нода __init__: запускает сценарий и удаляет себя.")
                .events("on_init").inputs("create");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                HANDLER.fieldOf("on_init", EMPTY_HANDLER)
        );
        private final EventHandler init;

        public Config(EventHandler init) {
            this.init = init;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new InitComponent(this, name, airdrop.components());
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
