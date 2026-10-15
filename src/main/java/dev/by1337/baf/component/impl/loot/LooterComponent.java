package dev.by1337.baf.component.impl.loot;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.BAF;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.IntValue;
import dev.by1337.baf.config.value.MaterialValue;
import dev.by1337.baf.config.value.StringValue;
import dev.by1337.baf.config.value.Vec3iValue;
import dev.by1337.baf.interact.InteractListener;
import dev.by1337.baf.loot.LootTable;
import dev.by1337.baf.loot.inventory.LootingInventory;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
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
        var offsets = config.offsets.getVector(airdrop);
        int cooldown = config.takeCooldownTicks.getInt(airdrop);
        int fillTicks = config.fillDurationTicks.getInt(airdrop);
        int rolls = config.rolls.getInt(airdrop);
        int size = config.size.getInt(airdrop);
        int interval = config.fillIntervalTicks.getInt(airdrop);
        int intervalMax = upperBound(interval, config.fillIntervalMaxTicks.getInt(airdrop));
        int reveal = config.revealDelayTicks.getInt(airdrop);
        int revealMax = upperBound(reveal, config.revealDelayMaxTicks.getInt(airdrop));
        validateTimings(fillTicks, interval, intervalMax, reveal, revealMax);
        var materials = config.obfuscationMaterials.stream().map(value -> value.getMaterial(airdrop)).toList();
        if (materials.stream().anyMatch(m -> !m.isItem() || m.isAir()))
            throw new IllegalArgumentException("Obfuscation materials must be non-air items");
        String maskName = config.obfuscationName.getString(airdrop);
        String lootId = config.lootTable.getString(airdrop);
        try (var ignored = ctx.tracer().enter(name + " create {", "}")) {
            clickHolder.set(BAF.blockInteract().registerBlockClick(
                    airdrop.world(),
                    airdrop.x() + offsets.x(),
                    airdrop.y() + offsets.y(),
                    airdrop.z() + offsets.z(),
                    LooterComponent.this::on
            ));
            inventoryHolder.set(new LootingInventory(
                    size,
                    MiniMessage.deserialize(config.title.getString(airdrop)),
                    airdrop.location().add(offsets.x(), offsets.y(), offsets.z()),
                    airdrop.manager().plugin(),
                    cooldown, materials,
                    maskName.isEmpty() ? null : MiniMessage.deserialize(maskName), reveal, revealMax
            ));
            LootTable lootTable = airdrop.manager().lootTables().get(lootId);
            if (lootTable == null) {
                log.error("Unknown loot table {}", lootId);
            } else {
                var inv = inventoryHolder.get();
                if (inv == null) return;
                inv.fill(lootTable.generate(rolls), fillTicks, interval, intervalMax);
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


    private static int upperBound(int min, int max) {
        return max == 0 ? min : max;
    }

    private static void validateTimings(int duration, int interval, int intervalMax, int reveal, int revealMax) {
        if (intervalMax < interval || (interval == 0 && intervalMax != 0))
            throw new IllegalArgumentException("Invalid fill interval range");
        if (revealMax < reveal || (reveal == 0 && revealMax != 0))
            throw new IllegalArgumentException("Invalid reveal delay range");
        if (duration > 0 && interval > 0)
            throw new IllegalArgumentException("fill_duration_ticks and fill_interval_ticks cannot be used together");
    }

    public static class Config implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Контейнер лута", "Открывает инвентарь с лутом при взаимодействии с блоком.")
                .vector("offsets", "Смещение блока", "0;0;0", false)
                .text("loot_table", "Таблица лута", "loot/default", true)
                .integer("rolls", "Количество выборок лута (с повторениями)", 1, 0, false)
                .text("title", "Заголовок инвентаря", "&7Title", false)
                .integer("size", "Размер GUI (9–54, кратно 9)", 54, 9, false)
                .integer("fill_interval_ticks", "Минимальный интервал добавления, тиков (0 — выключен)", 0, 0, false)
                .integer("fill_interval_max_ticks", "Максимальный интервал (0 — равен минимальному)", 0, 0, false)
                .integer("reveal_delay_ticks", "Минимальное остывание маски, тиков (0 — раскрытие при подборе)", 0, 0, false)
                .integer("reveal_delay_max_ticks", "Максимальное остывание (0 — равно минимальному)", 0, 0, false)
                .integer("take_cooldown_ticks", "КД взятия, тиков (-1 — выключен)", -1, -1, false)
                .integer("fill_duration_ticks", "Длительность заполнения, тиков", 0, 0, false)
                .materials("obfuscation_materials", "Материалы масок (пусто — выключено)")
                .text("obfuscation_name", "Общее имя масок", "", false)
                .events("on_create", "on_open", "on_remove")
                .inputs("create", "remove");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
                Config::new,
                Vec3iValue.DECODER.fieldOf("offsets", new Vec3iValue(Vec3i.ZERO)),
                StringValue.DECODER.fieldOf("loot_table"),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_open", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER),
                StringValue.DECODER.fieldOf("title", new StringValue("&7Title")),
                IntValue.DECODER.fieldOf("take_cooldown_ticks", new IntValue(-1)),
                IntValue.DECODER.fieldOf("fill_duration_ticks", new IntValue(0)),
                MaterialValue.DECODER.listOf().fieldOf("obfuscation_materials", List.of()),
                StringValue.DECODER.fieldOf("obfuscation_name", new StringValue("")),
                IntValue.DECODER.fieldOf("rolls", new IntValue(1)),
                IntValue.DECODER.fieldOf("size", new IntValue(54)),
                IntValue.DECODER.fieldOf("fill_interval_ticks", new IntValue(0)),
                IntValue.DECODER.fieldOf("fill_interval_max_ticks", new IntValue(0)),
                IntValue.DECODER.fieldOf("reveal_delay_ticks", new IntValue(0)),
                IntValue.DECODER.fieldOf("reveal_delay_max_ticks", new IntValue(0))
        );
        private final Vec3iValue offsets;
        private final StringValue lootTable;
        private final IntValue rolls;
        private final IntValue size;
        private final IntValue fillIntervalTicks;
        private final IntValue fillIntervalMaxTicks;
        private final IntValue revealDelayTicks;
        private final IntValue revealDelayMaxTicks;
        private final EventHandler create;
        private final EventHandler open;
        private final EventHandler remove;
        private final StringValue title;
        private final IntValue takeCooldownTicks;
        private final IntValue fillDurationTicks;
        private final List<MaterialValue> obfuscationMaterials;
        private final StringValue obfuscationName;

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
            this(new Vec3iValue(offsets), new StringValue(lootTable), create, open, remove, new StringValue(title),
                    new IntValue(takeCooldownTicks), new IntValue(fillDurationTicks),
                    obfuscationMaterials.stream().map(MaterialValue::new).toList(), new StringValue(obfuscationName), new IntValue(rolls));
        }

        public Config(Vec3iValue offsets, StringValue lootTable, EventHandler create, EventHandler open, EventHandler remove,
                      StringValue title, IntValue takeCooldownTicks, IntValue fillDurationTicks,
                      List<MaterialValue> obfuscationMaterials, StringValue obfuscationName, IntValue rolls) {
            this(offsets, lootTable, create, open, remove, title, takeCooldownTicks, fillDurationTicks,
                    obfuscationMaterials, obfuscationName, rolls, new IntValue(54), new IntValue(0), new IntValue(0),
                    new IntValue(0), new IntValue(0));
        }

        public Config(Vec3iValue offsets, StringValue lootTable, EventHandler create, EventHandler open, EventHandler remove,
                      StringValue title, IntValue takeCooldownTicks, IntValue fillDurationTicks,
                      List<MaterialValue> obfuscationMaterials, StringValue obfuscationName, IntValue rolls,
                      IntValue size, IntValue fillIntervalTicks, IntValue fillIntervalMaxTicks,
                      IntValue revealDelayTicks, IntValue revealDelayMaxTicks) {
            this.size = size.checked(v -> v >= 9 && v <= 54 && v % 9 == 0, "size must be a multiple of 9 between 9 and 54");
            this.fillIntervalTicks = fillIntervalTicks.checked(v -> v >= 0, "fill_interval_ticks must be >= 0");
            this.fillIntervalMaxTicks = fillIntervalMaxTicks.checked(v -> v >= 0, "fill_interval_max_ticks must be >= 0");
            this.revealDelayTicks = revealDelayTicks.checked(v -> v >= 0, "reveal_delay_ticks must be >= 0");
            this.revealDelayMaxTicks = revealDelayMaxTicks.checked(v -> v >= 0, "reveal_delay_max_ticks must be >= 0");
            if (fillDurationTicks.isConstant() && fillIntervalTicks.isConstant() && fillIntervalMaxTicks.isConstant()
                    && revealDelayTicks.isConstant() && revealDelayMaxTicks.isConstant()) {
                int interval = this.fillIntervalTicks.getInt(null);
                int reveal = this.revealDelayTicks.getInt(null);
                validateTimings(fillDurationTicks.getInt(null), interval,
                        upperBound(interval, this.fillIntervalMaxTicks.getInt(null)), reveal,
                        upperBound(reveal, this.revealDelayMaxTicks.getInt(null)));
            }
            this.rolls = rolls.checked(v -> v >= 0, "rolls must be >= 0");
            this.takeCooldownTicks = takeCooldownTicks.checked(v -> v >= -1, "take_cooldown_ticks must be >= -1");
            this.fillDurationTicks = fillDurationTicks.checked(v -> v >= 0, "fill_duration_ticks must be >= 0");
            for (var material : obfuscationMaterials) {
                if (material.isConstant() && (!material.getMaterial(null).isItem() || material.getMaterial(null).isAir()))
                    throw new IllegalArgumentException("Obfuscation materials must be non-air items");
            }
            this.obfuscationMaterials = List.copyOf(obfuscationMaterials);
            this.obfuscationName = obfuscationName;
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