package dev.by1337.baf.component.impl.loot;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.baf.BAF;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.interact.InteractListener;
import dev.by1337.baf.loot.LootTable;
import dev.by1337.baf.loot.inventory.LootingInventory;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.BukkitYamlDecoders;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LooterComponent extends AbstractComponent {

    private static final Logger log = LoggerFactory.getLogger(LooterComponent.class);
    private final Config config;
    private final Airdrop airdrop;
    private final CleanerSlot<InteractListener<PlayerInteractEvent>> clickHolder;
    private final CleanerSlot<LootingInventory> inventoryHolder;

    private LooterComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.config = config;
        this.airdrop = airdrop;
        clickHolder = new CleanerSlot<>(InteractListener::unregister, airdrop.cleaner());
        inventoryHolder = new CleanerSlot<>(LootingInventory::close, airdrop.cleaner());
    }

    private void on(PlayerInteractEvent event) {
        event.setCancelled(true);
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var inv = inventoryHolder.get();
        if (inv == null) return;
        Player pl = event.getPlayer();
        if (pl.getOpenInventory().getTopInventory() == inv.getInventory()) {
            return;
        }
        pl.openInventory(inv.getInventory());
        try (
                var ctx = BAF.newContext(airdrop, pl);
                var ignored = ctx.tracer().enter(name + " open {", "}")
        ) {
            config.open.handle(ctx);
        }
    }

    @Override
    public void onCreate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " create {", "}")) {
            clickHolder.set(BAF.blockInteract().registerBlockClick(
                    airdrop.world(),
                    airdrop.x() + config.offsets.x(),
                    airdrop.y() + config.offsets.y(),
                    airdrop.z() + config.offsets.z(),
                    LooterComponent.this::on
            ));
            inventoryHolder.set(new LootingInventory(
                    54,
                    MiniMessage.deserialize(ctx.setPlaceholders(config.title)),
                    airdrop.location().add(config.offsets.x(), config.offsets.y(), config.offsets.z()),
                    airdrop.manager().plugin(),
                    config.takeCooldownTicks, config.obfuscationMaterials,
                    config.obfuscationName == null ? null : MiniMessage.deserialize(ctx.setPlaceholders(config.obfuscationName))
            ));
            LootTable lootTable = airdrop.manager().lootTables().get(ctx.setPlaceholders(config.lootTable));
            if (lootTable == null) {
                log.error("Unknown loot table {}", ctx.setPlaceholders(config.lootTable));
            } else {
                var inv = inventoryHolder.get();
                if (inv == null) return;
                inv.fill(lootTable.generate(config.rolls), config.fillDurationTicks);
            }
            config.create.handle(ctx);
        }
    }

    @Override
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " remove {", "}")) {
            config.remove.handle(ctx);
            clickHolder.cleanup();
            inventoryHolder.cleanup();
        }
    }


    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Контейнер лута", "Открывает инвентарь с лутом при взаимодействии с блоком.")
                .vector("offsets", "Смещение блока", "0;0;0", false)
                .text("loot_table", "Таблица лута", "loot/default", true)
                .integer("rolls", "Количество выборок лута (с повторениями)", 1, 0, false)
                .text("title", "Заголовок инвентаря", "&7Title", false)
                .integer("take_cooldown_ticks", "КД взятия, тиков (-1 — выключен)", -1, -1, false)
                .integer("fill_duration_ticks", "Длительность заполнения, тиков", 0, 0, false)
                .materials("obfuscation_materials", "Материалы масок (пусто — выключено)")
                .text("obfuscation_name", "Общее имя масок", "", false)
                .events("on_create", "on_open", "on_remove")
                .inputs("create", "remove");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                Vec3i.DECODER.fieldOf("offsets", new Vec3i(0, 0, 0)),
                YamlDecoder.STRING.fieldOf("loot_table"),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_open", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER),
                YamlDecoder.STRING.fieldOf("title", "&7Title"),
                YamlDecoder.INT.fieldOf("take_cooldown_ticks", -1),
                YamlDecoder.INT.fieldOf("fill_duration_ticks", 0),
                BukkitYamlDecoders.material().listOf().fieldOf("obfuscation_materials", List.of()),
                YamlDecoder.STRING.fieldOf("obfuscation_name", ""),
                YamlDecoder.INT.fieldOf("rolls", 1)
        );
        private final Vec3i offsets;
        private final String lootTable;
        private final int rolls;
        private final EventHandler create;
        private final EventHandler open;
        private final EventHandler remove;
        private final String title;
        private final int takeCooldownTicks;
        private final int fillDurationTicks;
        private final List<Material> obfuscationMaterials;
        private final String obfuscationName;

        public Config(Vec3i offsets, String lootTable, EventHandler create, EventHandler open, EventHandler remove, String title) {
            this(offsets, lootTable, create, open, remove, title, -1, 0, List.of(), "");
        }

        public Config(Vec3i offsets, String lootTable, EventHandler create, EventHandler open, EventHandler remove,
                      String title, int takeCooldownTicks, int fillDurationTicks,
                      List<Material> obfuscationMaterials, String obfuscationName) {
            this(offsets, lootTable, create, open, remove, title, takeCooldownTicks, fillDurationTicks,
                    obfuscationMaterials, obfuscationName, 1);
        }

        public Config(Vec3i offsets, String lootTable, EventHandler create, EventHandler open, EventHandler remove,
                      String title, int takeCooldownTicks, int fillDurationTicks,
                      List<Material> obfuscationMaterials, String obfuscationName, int rolls) {
            if (rolls < 0) throw new IllegalArgumentException("rolls must be >= 0");
            this.rolls = rolls;
            if (takeCooldownTicks < -1) throw new IllegalArgumentException("take_cooldown_ticks must be >= -1");
            if (fillDurationTicks < 0) throw new IllegalArgumentException("fill_duration_ticks must be >= 0");
            if (obfuscationMaterials.stream().anyMatch(m -> !m.isItem() || m.isAir()))
                throw new IllegalArgumentException("Obfuscation materials must be non-air items");
            this.takeCooldownTicks = takeCooldownTicks;
            this.fillDurationTicks = fillDurationTicks;
            this.obfuscationMaterials = List.copyOf(obfuscationMaterials);
            this.obfuscationName = obfuscationName.isEmpty() ? null : obfuscationName;
            this.offsets = offsets;
            this.lootTable = lootTable;
            this.create = create;
            this.open = open;
            this.remove = remove;
            this.title = title;
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new LooterComponent(this, name, airdrop);
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}