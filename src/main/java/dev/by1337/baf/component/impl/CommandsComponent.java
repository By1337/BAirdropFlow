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

public class CommandsComponent extends AbstractComponent {
    private final Config config;

    private CommandsComponent(Config config, String name, ComponentHolder owner) {
        super(name, owner);
        this.config = config;
    }

    @Override
    public void onCreate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " run {", "}")) {
            try {
                config.onRun.handle(ctx);
            } finally {
                if (!isRemoved()) markRemoved(ctx);
            }
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
    }

    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Команды", "Выполняет список команд и сразу удаляет себя.")
                .events("on_run").inputs("run");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                HANDLER.fieldOf("on_run", EMPTY_HANDLER)
        );
        private final EventHandler onRun;

        public Config(EventHandler onRun) {
            this.onRun = onRun;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new CommandsComponent(this, name, airdrop.components());
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
