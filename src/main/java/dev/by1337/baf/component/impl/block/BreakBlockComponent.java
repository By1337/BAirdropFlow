package dev.by1337.baf.component.impl.block;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.BAF;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.IntValue;
import dev.by1337.baf.config.value.Vec3iValue;
import dev.by1337.baf.interact.InteractListener;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
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
        exportPlaceholder("count", () -> count);
        cleaner = new CleanerSlot<>(InteractListener::unregister, airdrop.cleaner());
    }

    @Override
    public void onCreate(EventContext ctx) {
        count = config.count.getInt(airdrop);
        var offsets = config.offsets.getVector(airdrop);
        try (var ignored = ctx.tracer().enter(name + " on_create {", "}")) {
            cleaner.set(BAF.blockInteract().registerBlockBreak(
                    airdrop.world(),
                    airdrop.x() + offsets.x(),
                    airdrop.y() + offsets.y(),
                    airdrop.z() + offsets.z(),
                    this::on
            ));
            config.on_create.handle(ctx);
        }
    }

    private void on(BlockBreakEvent event) {
        event.setCancelled(true);
        onBreak(BAF.newContext(airdrop, event.getPlayer()));
    }

    public void onBroken(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_broken {", "}")) {
            config.on_broken.handle(ctx);
            markRemoved(ctx);
        }
    }

    public void onBreak(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_break {", "}")) {
            if (--count == 0) {
                onBroken(ctx);
            } else {
                config.on_break.handle(ctx);
            }
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_remove {", "}")) {
            cleaner.cleanup();
            config.on_remove.handle(ctx);
        }
    }

    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Разрушение блока", "Считает попытки сломать блок. Плейсхолдер {ctx:имя.count}.")
                .placeholder("count", "Осталось попыток", "Число попыток разрушения до завершения.")
                .vector("offsets", "Смещение блока", "0;0;0", false).integer("count", "Число попыток", 10, 1, true).events("on_create", "on_break", "on_broken", "on_remove").inputs("create", "remove");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                Vec3iValue.DECODER.fieldOf("offsets", new Vec3iValue(Vec3i.ZERO)),
                IntValue.DECODER.fieldOf("count"),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_break", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER),
                HANDLER.fieldOf("on_broken", EMPTY_HANDLER)
        );
        private final Vec3iValue offsets;
        private final IntValue count;
        private final EventHandler on_create;
        private final EventHandler on_break;
        private final EventHandler on_remove;
        private final EventHandler on_broken;

        public Config(Vec3i offsets, int count, EventHandler on_create, EventHandler on_break, EventHandler on_remove, EventHandler onBroken) {
            this(new Vec3iValue(offsets), new IntValue(count), on_create, on_break, on_remove, onBroken);
        }

        public Config(Vec3iValue offsets, IntValue count, EventHandler on_create, EventHandler on_break, EventHandler on_remove, EventHandler onBroken) {
            this.offsets = offsets;
            this.count = count.checked(v -> v > 0, "Count must be positive");
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
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
