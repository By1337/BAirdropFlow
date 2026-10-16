package dev.by1337.baf.component.impl.loot;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.BAF;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.baf.config.value.*;
import dev.by1337.baf.loot.spreading.SpreadingItems;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.baf.util.Vec3d;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

import java.util.List;

public final class SpreadingComponent extends AbstractComponent implements TimerSystem.Tickable {
    private final Config config;
    private final Airdrop airdrop;
    private final CleanerSlot<SpreadingItems> items;
    private boolean spreadComplete;

    private SpreadingComponent(Config config, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.config = config;
        this.airdrop = airdrop;
        items = new CleanerSlot<>(SpreadingItems::close, airdrop.cleaner());
    }

    @Override
    public void onCreate(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " create {", "}")) {
            String lootId = config.lootTable.getString(airdrop);
            var table = airdrop.manager().lootTables().get(lootId);
            if (table == null) throw new IllegalArgumentException("Unknown loot table " + lootId);
            String animation = config.trailAnimation.getString(airdrop);
            var graph = animation.isBlank() ? null : BAF.particleGraphs().get(animation);
            if (!animation.isBlank() && graph == null)
                throw new IllegalArgumentException("Unknown particle animation " + animation);
            var offset = config.offsets.getVector(airdrop);
            String maskName = config.obfuscationName.getString(airdrop);
            items.set(new SpreadingItems(airdrop.manager().plugin(),
                    airdrop.location().add(offset.x(), offset.y(), offset.z()),
                    table.generate(config.rolls.getInt(airdrop)), config.intervalTicks.getInt(airdrop),
                    config.horizontalSpeed.getDouble(airdrop), config.verticalSpeed.getDouble(airdrop),
                    config.pickupDelayTicks.getInt(airdrop), config.itemLifetimeTicks.getInt(airdrop),
                    config.obfuscationMaterials.stream().map(v -> v.getMaterial(airdrop)).toList(),
                    maskName.isEmpty() ? null : MiniMessage.deserialize(maskName), graph,
                    config.trailIntervalTicks.getInt(airdrop)).spreadOver(config.spreadDurationTicks.getInt(airdrop)));
            config.onCreate.handle(ctx);
        }
    }

    @Override
    public void onSyncTick(EventContext ctx) {
        if (isRemoved()) return;
        var current = items.get();
        if (current == null || current.isClosed()) return;
        current.tick();
        if (!spreadComplete && current.isSpreadComplete()) {
            spreadComplete = true;
            try (var ignored = ctx.tracer().enter(name + " spread_end {", "}")) {
                config.onSpreadEnd.handle(ctx);
            }
        }
        if (!isRemoved() && current.isFinished()) markRemoved(ctx);
    }

    @Override
    public void onRemove(EventContext ctx) {
        try (var ignored = ctx.tracer().enter(name + " remove {", "}")) {
            try {
                config.onRemove.handle(ctx);
            } finally {
                items.cleanup();
            }
        }
    }

    public record Config(StringValue lootTable, IntValue rolls, Vec3dValue offsets, IntValue intervalTicks,
                         DoubleValue horizontalSpeed, DoubleValue verticalSpeed, IntValue pickupDelayTicks,
                         IntValue itemLifetimeTicks, List<MaterialValue> obfuscationMaterials,
                         StringValue obfuscationName, StringValue trailAnimation, IntValue trailIntervalTicks,
                         EventHandler onCreate, EventHandler onSpreadEnd,
                         EventHandler onRemove, IntValue spreadDurationTicks) implements ComponentFactory {
        public Config(StringValue lootTable, IntValue rolls, Vec3dValue offsets, IntValue intervalTicks,
                      DoubleValue horizontalSpeed, DoubleValue verticalSpeed, IntValue pickupDelayTicks,
                      IntValue itemLifetimeTicks, List<MaterialValue> obfuscationMaterials,
                      StringValue obfuscationName, StringValue trailAnimation, IntValue trailIntervalTicks,
                      EventHandler onCreate, EventHandler onSpreadEnd, EventHandler onRemove) {
            this(lootTable, rolls, offsets, intervalTicks, horizontalSpeed, verticalSpeed, pickupDelayTicks,
                    itemLifetimeTicks, obfuscationMaterials, obfuscationName, trailAnimation, trailIntervalTicks,
                    onCreate, onSpreadEnd, onRemove, new IntValue(0));
        }

        public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Разброс лута", "Выбрасывает лут по очереди с маскировкой до подбора и трейлами в полёте. Удаление ноды убирает оставшиеся предметы.")
                .text("loot_table", "Таблица лута", "loot/default", true)
                .integer("rolls", "Количество выборок лута (с повторениями)", 10, 0, false)
                .vector("offsets", "Смещение точки выброса", "0.5;1;0.5", true)
                .integer("interval_ticks", "Интервал выброса, тиков", 2, 1, false)
                .integer("spread_duration_ticks", "Выбросить весь лут за N тиков (0 — интервалы)", 0, 0, false)
                .number("horizontal_speed", "Горизонтальная скорость", 0.4, 0, null)
                .number("vertical_speed", "Скорость вверх", 0.6, 0, null)
                .integer("pickup_delay_ticks", "Задержка подбора, тиков", 20, 0, false)
                .integer("item_lifetime_ticks", "Время жизни каждого предмета, тиков", 1200, 1, false)
                .materials("obfuscation_materials", "Материалы масок (пусто — выключено)")
                .text("obfuscation_name", "Общее имя масок", "", false)
                .text("trail_animation", "ID анимации из particles (пусто — выключено)", "", false)
                .integer("trail_interval_ticks", "Интервал трейла, тиков", 1, 1, false)
                .events("on_create", "on_spread_end", "on_remove")
                .inputs("create", "remove");

        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new,
                StringValue.DECODER.fieldOf("loot_table"),
                IntValue.DECODER.fieldOf("rolls", new IntValue(10)),
                Vec3dValue.DECODER.fieldOf("offsets", new Vec3dValue(new Vec3d(0.5, 1, 0.5))),
                IntValue.DECODER.fieldOf("interval_ticks", new IntValue(2)),
                DoubleValue.DECODER.fieldOf("horizontal_speed", new DoubleValue(0.4)),
                DoubleValue.DECODER.fieldOf("vertical_speed", new DoubleValue(0.6)),
                IntValue.DECODER.fieldOf("pickup_delay_ticks", new IntValue(20)),
                IntValue.DECODER.fieldOf("item_lifetime_ticks", new IntValue(1200)),
                MaterialValue.DECODER.listOf().fieldOf("obfuscation_materials", List.of()),
                StringValue.DECODER.fieldOf("obfuscation_name", new StringValue("")),
                StringValue.DECODER.fieldOf("trail_animation", new StringValue("")),
                IntValue.DECODER.fieldOf("trail_interval_ticks", new IntValue(1)),
                HANDLER.fieldOf("on_create", EMPTY_HANDLER),
                HANDLER.fieldOf("on_spread_end", EMPTY_HANDLER),
                HANDLER.fieldOf("on_remove", EMPTY_HANDLER),
                IntValue.DECODER.fieldOf("spread_duration_ticks", new IntValue(0)));

        public Config {
            spreadDurationTicks = spreadDurationTicks.checked(v -> v >= 0, "spread_duration_ticks must be >= 0");
            rolls = rolls.checked(v -> v >= 0, "rolls must be >= 0");
            intervalTicks = intervalTicks.checked(v -> v > 0, "interval_ticks must be positive");
            horizontalSpeed = horizontalSpeed.checked(v -> v >= 0, "horizontal_speed must be >= 0");
            verticalSpeed = verticalSpeed.checked(v -> v >= 0, "vertical_speed must be >= 0");
            pickupDelayTicks = pickupDelayTicks.checked(v -> v >= 0, "pickup_delay_ticks must be >= 0");
            itemLifetimeTicks = itemLifetimeTicks.checked(v -> v > 0, "item_lifetime_ticks must be positive");
            trailIntervalTicks = trailIntervalTicks.checked(v -> v > 0, "trail_interval_ticks must be positive");
            obfuscationMaterials = List.copyOf(obfuscationMaterials);
            for (var value : obfuscationMaterials) {
                if (value.isConstant() && (!value.getMaterial(null).isItem() || value.getMaterial(null).isAir()))
                    throw new IllegalArgumentException("Obfuscation materials must be non-air items");
            }
        }

        @Override
        public AirdropComponent create(Airdrop airdrop, String name) {
            return new SpreadingComponent(this, name, airdrop);
        }

        @Override
        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
