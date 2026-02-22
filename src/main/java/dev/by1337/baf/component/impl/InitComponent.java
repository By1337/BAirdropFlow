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

public class InitComponent extends AbstractComponent {
    private final Config config;

    private InitComponent(Config config, String name, ComponentHolder owner) {
        super(name, owner);
        this.config = config;
       // exportEvent("init", this::onInit);
    }

    public void onInit(AirdropContext ctx) {
        ctx.log(name + " init");
        config.init.handle(ctx);
        markRemoved(ctx);
    }

    @Override
    public void onCreate(AirdropContext ctx) {
        ctx.log(name + " create");
        onInit(ctx); //proxy
    }

    @Override
    public void onRemove(AirdropContext ctx) {
        ctx.log(name + " remove");
    }

    public static class Config implements ComponentFactory {
        public static final YamlCodec<Config> CODEC = RecordYamlCodecBuilder.mapOf(
                Config::new,
                ComponentHandler.CODEC.fieldOf("on_init", v -> v.init, ComponentHandler.EMPTY)
        );
        private final ComponentHandler init;

        public Config(ComponentHandler init) {
            this.init = init;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new InitComponent(this, name, airdrop.components());
        }

        @Override
        public YamlCodec<? extends ComponentFactory> codec() {
            return CODEC;
        }
    }
}
