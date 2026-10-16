package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.*;
import dev.by1337.baf.util.Vec3d;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.util.Vector;

import java.util.List;

public final class ItemsComponent extends FtComponent {
    private final Config config;
    private int emitted, next, wave, rolls, waveSize, interval, gap, duration;
    private Vec3d min, max;

    private ItemsComponent(Config c, String name, Airdrop air) {
        super(c.common, name, air);
        config = c;
    }

    @Override
    protected void start(EventContext ctx) {
        rolls = config.rolls.getInt(airdrop);
        duration = config.spreadDuration.getInt(airdrop);
        waveSize = config.waveSize.getInt(airdrop);
        interval = config.interval.getInt(airdrop);
        gap = config.waveInterval.getInt(airdrop);
        min = config.velocityMin.getVector(airdrop);
        max = config.velocityMax.getVector(airdrop);
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z())
            throw new IllegalArgumentException("Invalid velocity range");
    }

    @Override
    protected void tick(EventContext ctx) {
        int due = duration == 0 ? (age >= next ? emitted + 1 : emitted)
                : (int) (Math.min((long) age + 1, duration) * rolls / duration);
        while (!isRemoved() && emitted < rolls && emitted < due) {
            boolean reverse = config.alternate.getBoolean(airdrop) && (wave % 2 == 1);
            if (emitted % waveSize == 0) emit(config.onWave, origin, null, null);
            if (isRemoved()) return;
            boolean success = scatter(origin, config.lootTable.getString(airdrop), 1, config.masks.stream().map(v -> v.getMaterial(airdrop)).toList(), config.maskName.getString(airdrop), item -> {
                double x = random(min.x(), max.x());
                item.setVelocity(new Vector(reverse ? -x : x, random(min.y(), max.y()), random(min.z(), max.z())));
                item.setFireTicks(config.fireTicks.getInt(airdrop));
                item.setInvulnerable(true);
                item.setPickupDelay(config.pickupDelay.getInt(airdrop));
                if (item.getItemStack().hasItemMeta()) item.customName(item.getItemStack().getItemMeta().displayName());
                item.setCustomNameVisible(config.visibleName.getBoolean(airdrop));
            });
            if (success) emit(config.onEachSpread, origin, null, null);
            emitted++;
            next = age + interval;
            if (emitted % waveSize == 0) {
                wave++;
                next = age + gap;
            }
        }
        if (emitted >= rolls && dropsFinished()) finish(ctx);
    }

    public record Config(FtSettings common, StringValue lootTable, IntValue rolls, IntValue waveSize, IntValue interval,
                         IntValue waveInterval,
                         Vec3dValue velocityMin, Vec3dValue velocityMax, BooleanValue alternate, IntValue fireTicks,
                         IntValue pickupDelay,
                         BooleanValue visibleName, List<MaterialValue> masks, StringValue maskName,
                         dev.by1337.edsl.EventHandler onEachSpread,
                         dev.by1337.edsl.EventHandler onWave, IntValue spreadDuration) implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = FtSettings.schema("FT: волны предметов", "Вулкан/Санта: существующий лут, диапазоны скорости, волны и безопасная очистка.")
                .text("loot_table", "Таблица лута", "", true).integer("rolls", "Число предметов", 20, 0, false)
                .integer("wave_size", "Предметов в волне", 5, 1, false).integer("interval_ticks", "Между предметами", 5, 1, false)
                .integer("wave_interval_ticks", "Между волнами", 60, 1, false)
                .integer("spread_duration_ticks", "Выбросить весь лут за N тиков (0 — интервалы волн)", 0, 0, false)
                .vector("velocity_min", "Минимальная скорость", "0.4;0.3;-0.25", true).vector("velocity_max", "Максимальная скорость", "0.9;0.6;0.25", true)
                .bool("alternate_x", "Чередовать направление волн", true).integer("fire_ticks", "Горение предмета", 100, 0, false)
                .integer("pickup_delay_ticks", "Задержка подбора", 60, 0, false).bool("item_name_visible", "Показывать имя", true)
                .materials("obfuscation_materials", "Маски").text("obfuscation_name", "Имя масок", "", false).events("on_each_spread", "on_wave");
        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new, FtSettings.DECODER.fieldOf(),
                StringValue.DECODER.fieldOf("loot_table"), IntValue.DECODER.fieldOf("rolls", new IntValue(20)),
                IntValue.DECODER.fieldOf("wave_size", new IntValue(5)), IntValue.DECODER.fieldOf("interval_ticks", new IntValue(5)),
                IntValue.DECODER.fieldOf("wave_interval_ticks", new IntValue(60)),
                Vec3dValue.DECODER.fieldOf("velocity_min", new Vec3dValue(new Vec3d(.4, .3, -.25))),
                Vec3dValue.DECODER.fieldOf("velocity_max", new Vec3dValue(new Vec3d(.9, .6, .25))), BooleanValue.DECODER.fieldOf("alternate_x", new BooleanValue(true)),
                IntValue.DECODER.fieldOf("fire_ticks", new IntValue(100)), IntValue.DECODER.fieldOf("pickup_delay_ticks", new IntValue(60)),
                BooleanValue.DECODER.fieldOf("item_name_visible", new BooleanValue(true)), MaterialValue.DECODER.listOf().fieldOf("obfuscation_materials", List.of()),
                StringValue.DECODER.fieldOf("obfuscation_name", new StringValue("")),
                HANDLER.fieldOf("on_each_spread", EMPTY_HANDLER), HANDLER.fieldOf("on_wave", EMPTY_HANDLER),
                IntValue.DECODER.fieldOf("spread_duration_ticks", new IntValue(0)));

        public Config {
            spreadDuration = spreadDuration.checked(v -> v >= 0, "Invalid spread_duration_ticks");
            rolls = rolls.checked(v -> v >= 0 && v <= 4096, "Invalid rolls");
            waveSize = waveSize.checked(v -> v > 0 && v <= 4096, "Invalid wave_size");
            interval = interval.checked(v -> v > 0, "Invalid interval_ticks");
            waveInterval = waveInterval.checked(v -> v > 0, "Invalid wave_interval_ticks");
            fireTicks = fireTicks.checked(v -> v >= 0 && v <= 12000, "Invalid fire_ticks");
            pickupDelay = pickupDelay.checked(v -> v >= 0 && v <= 12000, "Invalid pickup_delay_ticks");
            masks = List.copyOf(masks);
        }

        public AirdropComponent create(Airdrop air, String name) {
            return new ItemsComponent(this, name, air);
        }

        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
