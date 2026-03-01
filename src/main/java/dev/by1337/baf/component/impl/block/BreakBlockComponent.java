package dev.by1337.baf.component.impl.block;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.command.AirdropContext;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.handler.ComponentHandler;
import dev.by1337.baf.interact.InteractListener;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;
import org.bukkit.event.block.BlockBreakEvent;

public class BreakBlockComponent extends AbstractComponent {
    private final Config config;
    private final Airdrop airdrop;
    private final CleanerSlot<InteractListener<BlockBreakEvent>> cleaner;
    private int count;

    private BreakBlockComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.airdrop = airdrop;
        this.config = config;
        count = config.count;
        exportPlaceholder("count", () -> count);
        cleaner = new CleanerSlot<>(InteractListener::unregister, airdrop.cleaner());
    }

    @Override
    public void onCreate(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_create {", "}")) {
            cleaner.set(airdrop.manager().interactManager().registerBlockBreak(
                    airdrop.world(),
                    airdrop.x() + config.offsets.x(),
                    airdrop.y() + config.offsets.y(),
                    airdrop.z() + config.offsets.z(),
                    this::on
            ));
            config.on_create.handle(ctx);
        }
    }

    private void on(BlockBreakEvent event) {
        event.setCancelled(true);
        onBreak(new AirdropContext(airdrop, event.getPlayer()));
    }

    public void onBroken(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_broken {", "}")) {
            config.on_broken.handle(ctx);
            markRemoved(ctx);
        }
    }

    public void onBreak(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_break {", "}")) {
            if (--count == 0) {
                onBroken(ctx);
            } else {
                config.on_break.handle(ctx);
            }
        }
    }

    @Override
    public void onRemove(AirdropContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_remove {", "}")) {
            cleaner.cleanup();
            config.on_remove.handle(ctx);
        }
    }

    public static class Config implements ComponentFactory {
        public static final YamlCodec<Config> CODEC = RecordYamlCodecBuilder.mapOf(
                Config::new,
                Vec3i.CODEC.fieldOf("offsets", v -> v.offsets, new Vec3i(0, 0, 0)),
                YamlCodec.INT.fieldOf("count", v -> v.count),
                ComponentHandler.CODEC.fieldOf("on_create", v -> v.on_create, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_break", v -> v.on_break, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_remove", v -> v.on_remove, ComponentHandler.EMPTY),
                ComponentHandler.CODEC.fieldOf("on_broken", v -> v.on_broken, ComponentHandler.EMPTY)
        );
        private final Vec3i offsets;
        private final int count;
        private final ComponentHandler on_create;
        private final ComponentHandler on_break;
        private final ComponentHandler on_remove;
        private final ComponentHandler on_broken;

        public Config(Vec3i offsets, int count, ComponentHandler on_create, ComponentHandler on_break, ComponentHandler on_remove, ComponentHandler onBroken) {
            this.offsets = offsets;
            this.count = count;
            this.on_create = on_create;
            this.on_break = on_break;
            this.on_remove = on_remove;
            on_broken = onBroken;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new BreakBlockComponent(this, name, airdrop);
        }

        @Override
        public YamlCodec<? extends ComponentFactory> codec() {
            return CODEC;
        }
    }
}