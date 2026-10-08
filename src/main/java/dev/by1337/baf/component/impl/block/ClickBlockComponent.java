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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class ClickBlockComponent extends AbstractComponent {
    private final Config config;
    private final Airdrop airdrop;
    private final CleanerSlot<InteractListener<PlayerInteractEvent>> cleaner;
    private int count;

    private ClickBlockComponent(Config config, String name, Airdrop airdrop) {
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
            cleaner.set(BAF.blockInteract().registerBlockClick(
                    airdrop.world(),
                    airdrop.x() + offsets.x(),
                    airdrop.y() + offsets.y(),
                    airdrop.z() + offsets.z(),
                    this::on
            ));
            config.on_create.handle(ctx);
        }
    }

    private void on(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK))
            return;
        event.setCancelled(true);
        try (var ctx = BAF.newContext(airdrop, event.getPlayer())) {
            onClick(ctx);
        }
    }

    public void onClicked(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " on_clicked {", "}")) {
            config.on_clicked.handle(ctx);
            markRemoved(ctx);
        }
    }

    public void onClick(EventContext ctx) {
        if (isRemoved() || count <= 0) return;
        try (var ignored = ctx.tracer().enter(name + " on_click {", "}")) {
            if (--count == 0) {
                onClicked(ctx);
            } else {
                config.on_click.handle(ctx);
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
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Клики по блоку", "Считает клики ЛКМ и ПКМ по блоку основной рукой. Плейсхолдер {ctx:имя.count}.")
                .placeholder("count", "Осталось кликов", "Число кликов до завершения.")
                .vector("offsets", "Смещение блока", "0;0;0", false)
                .integer("count", "Число кликов", 10, 1, true)
                .events("on_create", "on_click", "on_clicked", "on_remove")
                .inputs("create", "remove");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                Vec3iValue.DECODER.fieldOf("offsets", new Vec3iValue(Vec3i.ZERO)),
                IntValue.DECODER.fieldOf("count"),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_click", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER),
                HANDLER.fieldOf("on_clicked", EMPTY_HANDLER)
        );
        private final Vec3iValue offsets;
        private final IntValue count;
        private final EventHandler on_create;
        private final EventHandler on_click;
        private final EventHandler on_remove;
        private final EventHandler on_clicked;

        public Config(Vec3i offsets, int count, EventHandler on_create, EventHandler on_click, EventHandler on_remove, EventHandler onClicked) {
            this(new Vec3iValue(offsets), new IntValue(count), on_create, on_click, on_remove, onClicked);
        }

        public Config(Vec3iValue offsets, IntValue count, EventHandler on_create, EventHandler on_click, EventHandler on_remove, EventHandler onClicked) {
            this.offsets = offsets;
            this.count = count.checked(v -> v > 0, "Count must be positive");
            this.on_create = on_create;
            this.on_click = on_click;
            this.on_remove = on_remove;
            on_clicked = onClicked;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new ClickBlockComponent(this, name, airdrop);
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
