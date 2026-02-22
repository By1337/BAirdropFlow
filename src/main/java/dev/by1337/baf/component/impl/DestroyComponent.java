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
    public void onCreate(AirdropContext ctx) {
        ctx.log(name + " create");
        onDestroy(ctx); //proxy
    }

    @Override
    public void onRemove(AirdropContext ctx) {
        ctx.log(name + " remove");
    }

    public void onDestroy(AirdropContext ctx) {
        ctx.log(name + " destroy");
        config.destroy.handle(ctx);
        markRemoved(ctx);
    }

    public static class Config implements ComponentFactory {

        public static final YamlCodec<Config> CODEC = RecordYamlCodecBuilder.mapOf(
                Config::new,
                ComponentHandler.CODEC.fieldOf("on_destroy", v -> v.destroy, ComponentHandler.EMPTY)
        );
        private final ComponentHandler destroy;

        public Config(ComponentHandler destroy) {
            this.destroy = destroy;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new DestroyComponent(this, name, airdrop.components());
        }

        @Override
        public YamlCodec<? extends ComponentFactory> codec() {
            return CODEC;
        }
    }
}
