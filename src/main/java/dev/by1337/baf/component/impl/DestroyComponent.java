package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.component.ComponentHolder;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DestroyComponent extends AbstractComponent {
    private static final Logger log = LoggerFactory.getLogger("BAF#__destroy__");
    private final Config config;

    private DestroyComponent(Config config, String name, ComponentHolder owner) {
        super(name, owner);
        this.config = config;
        // exportEvent("destroy", this::onDestroy);
    }

    @Override
    public void onCreate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " create {", "}")) {
            onDestroy(ctx); //proxy
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
    }

    public void onDestroy(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " destroy {", "}")) {
            config.destroy.handle(ctx);
            markRemoved(ctx);
        }
    }

    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Удаление", "Финальная нода __destroy__: очистка блоков и ресурсов.")
                .events("on_destroy").inputs("destroy");


        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                HANDLER.fieldOf("on_destroy", EMPTY_HANDLER)
        );
        private final EventHandler destroy;

        public Config(EventHandler destroy) {
            this.destroy = destroy;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new DestroyComponent(this, name, airdrop.components());
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
