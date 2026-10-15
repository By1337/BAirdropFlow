package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.*;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.plc.Placeholders;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MobsComponent extends FtComponent {
    private final Config config;
    private final Map<UUID, Flight> flights = new HashMap<>();
    private int spawned;
    private int nextWave;
    private int total, interval, waveSize, maxAlive, liftTicks;
    private double hpMultiplier, minHealth, maxHealth;
    private List<EntityType> types;

    private record Flight(LivingEntity entity, int born, boolean launched, boolean airborne) {}

    private MobsComponent(Config c, String name, Airdrop air) {
        super(c.common, name, air);
        config = c;
        exportPlaceholder("spawned", () -> spawned);
        exportPlaceholder("alive", () -> entities == null ? 0 : entities.living().size());
    }

    @Override
    protected void start(EventContext ctx) {
        total = config.total.getInt(airdrop);
        interval = config.interval.getInt(airdrop);
        waveSize = config.waveSize.getInt(airdrop);
        maxAlive = config.maxAlive.getInt(airdrop);
        liftTicks = config.liftTicks.getInt(airdrop);
        hpMultiplier = config.hpMultiplier.getDouble(airdrop);
        minHealth = config.healthMin.getDouble(airdrop);
        maxHealth = config.healthMax.getDouble(airdrop);
        if (maxHealth < minHealth) throw new IllegalArgumentException("health_max must be >= health_min");
        types = config.types.stream().map(v -> v.get(airdrop)).toList();
        for (EntityType type : types)
            if (type.getEntityClass() == null || !Mob.class.isAssignableFrom(type.getEntityClass()))
                throw new IllegalArgumentException("Not a mob type: " + type);
    }

    @Override
    protected void tick(EventContext ctx) {
        entities.living().forEach(this::updateName);
        for (var flight : List.copyOf(flights.values())) {
            LivingEntity mob = flight.entity;
            if (!entities.owns(mob)) {
                flights.remove(mob.getUniqueId());
                continue;
            }
            if (!flight.launched && age - flight.born >= liftTicks) {
                mob.setGravity(true);
                double angle = random(0, Math.PI * 2), speed = config.horizontalSpeed.getDouble(airdrop);
                mob.setVelocity(new Vector(Math.cos(angle) * speed, config.verticalSpeed.getDouble(airdrop), Math.sin(angle) * speed));
                flights.put(mob.getUniqueId(), new Flight(mob, flight.born, true, false));
            } else if (!flight.launched) mob.setVelocity(new Vector(0, 0.05, 0));
            else if (!mob.isOnGround()) flights.put(mob.getUniqueId(), new Flight(mob, flight.born, true, true));
            else if (flight.airborne) {
                mob.setInvulnerable(false);
                flights.remove(mob.getUniqueId());
                landed(mob);
            }
        }
        if (age >= nextWave && (total == -1 || spawned < total)) {
            nextWave = age + interval;
            for (int i = 0; i < waveSize && entities.living().size() < maxAlive && (total == -1 || spawned < total); i++) {
                EntityType type = types.get(randomInt(0, types.size() - 1));
                var at = origin.clone().add(random(-1, 1), 1, random(-1, 1));
                @SuppressWarnings("unchecked") Class<? extends Mob> mobClass = (Class<? extends Mob>) type.getEntityClass();
                Mob mob = spawn(at, mobClass, true, value -> {
                    value.setRemoveWhenFarAway(false);
                    value.setCanPickupItems(false);
                    var reinforcements = value.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
                    if (reinforcements != null) reinforcements.setBaseValue(0);
                    var health = value.getAttribute(Attribute.MAX_HEALTH);
                    if (health == null) throw new IllegalArgumentException("Mob has no MAX_HEALTH");
                    double hp = minHealth > 0 ? random(minHealth, maxHealth) : health.getBaseValue() * hpMultiplier;
                    health.setBaseValue(hp);
                    value.setHealth(hp);
                    boolean launched = config.launch.getBoolean(airdrop);
                    value.setInvulnerable(launched);
                    value.setGravity(!launched || liftTicks == 0);
                    String equipment = config.equipmentTable.getString(airdrop);
                    if (!equipment.isBlank()) {
                        var table = airdrop.manager().lootTables().get(equipment);
                        if (table == null) throw new IllegalArgumentException("Unknown equipment_table " + equipment);
                        var stacks = table.generate(1);
                        if (!stacks.isEmpty()) {
                            value.getEquipment().setItemInMainHand(stacks.getFirst());
                            value.getEquipment().setItemInMainHandDropChance(0);
                        }
                    }
                    updateName(value);
                });
                if (mob != null) {
                    spawned++;
                    if (config.launch.getBoolean(airdrop))
                        flights.put(mob.getUniqueId(), new Flight(mob, age, false, false));
                }
            }
        }
        if (total != -1 && spawned >= total && entities.size() == 0) finish(ctx);
    }

    private void updateName(LivingEntity mob) {
        String text = config.mobName.getString(airdrop);
        if (text.isBlank()) return;
        var resolver = Placeholders.<LivingEntity>create().withContext("health", m -> Math.round(m.getHealth() * 10) / 10D)
                .withContext("max_health", m -> m.getAttribute(Attribute.MAX_HEALTH).getValue());
        mob.customName(MiniMessage.deserialize(resolver.setPlaceholders(text, mob)));
        mob.setCustomNameVisible(true);
    }

    @Override
    public void died(LivingEntity entity, Player killer) {
        flights.remove(entity.getUniqueId());
        if (killer != null)
            loot(entity.getLocation(), config.lootTable.getString(airdrop), config.rolls.getInt(airdrop), new Vector(0, 0.1, 0));
        super.died(entity, killer);
    }

    @Override
    protected void removed() {
        flights.clear();
    }

    public record Config(FtSettings common, List<Parameter<EntityType>> types, IntValue total, IntValue interval,
                         IntValue waveSize,
                         IntValue maxAlive, DoubleValue hpMultiplier, DoubleValue healthMin, DoubleValue healthMax,
                         BooleanValue launch, IntValue liftTicks, DoubleValue horizontalSpeed,
                         DoubleValue verticalSpeed,
                         StringValue mobName, StringValue equipmentTable, StringValue lootTable,
                         IntValue rolls) implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = FtSettings.schema("FT: мобы", "Несохраняемые мобы, волны, запуск и награды только за реальные смерти.")
                .strings("types", "Типы мобов", List.of("zombie")).integer("total", "Всего (-1 — пополнение без лимита)", 12, -1, false)
                .integer("interval_ticks", "Интервал волн", 160, 1, false).integer("wave_size", "Размер волны", 1, 1, false)
                .integer("max_alive", "Одновременно живых", 12, 1, false).number("health_multiplier", "Множитель HP", 5, 0.01, null)
                .number("health_min", "Минимум HP (0 — использовать множитель)", 0, 0, null).number("health_max", "Максимум HP", 0, 0, null)
                .bool("launch", "Поднять и бросить", true).integer("lift_ticks", "Тиков подъёма", 20, 0, false)
                .number("horizontal_speed", "Скорость по XZ", 0.5, 0, null).number("vertical_speed", "Скорость вверх", 1.2, 0, null)
                .text("mob_name", "Имя: {health}/{max_health}", "<red>{health}/{max_health}", false)
                .text("equipment_table", "Таблица предмета в руке", "", false).text("loot_table", "Таблица награды за убийство", "", false)
                .integer("rolls", "Выборок награды", 1, 0, false);
        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new, FtSettings.DECODER.fieldOf(),
                Parameter.enumDecoder(EntityType.class).listOf().fieldOf("types", List.of(Parameter.constant(EntityType.ZOMBIE))),
                IntValue.DECODER.fieldOf("total", new IntValue(12)), IntValue.DECODER.fieldOf("interval_ticks", new IntValue(160)),
                IntValue.DECODER.fieldOf("wave_size", new IntValue(1)), IntValue.DECODER.fieldOf("max_alive", new IntValue(12)),
                DoubleValue.DECODER.fieldOf("health_multiplier", new DoubleValue(5)), DoubleValue.DECODER.fieldOf("health_min", new DoubleValue(0)),
                DoubleValue.DECODER.fieldOf("health_max", new DoubleValue(0)), BooleanValue.DECODER.fieldOf("launch", new BooleanValue(true)),
                IntValue.DECODER.fieldOf("lift_ticks", new IntValue(20)), DoubleValue.DECODER.fieldOf("horizontal_speed", new DoubleValue(.5)),
                DoubleValue.DECODER.fieldOf("vertical_speed", new DoubleValue(1.2)), StringValue.DECODER.fieldOf("mob_name", new StringValue("<red>{health}/{max_health}")),
                StringValue.DECODER.fieldOf("equipment_table", new StringValue("")), StringValue.DECODER.fieldOf("loot_table", new StringValue("")),
                IntValue.DECODER.fieldOf("rolls", new IntValue(1)));

        public Config {
            types = List.copyOf(types);
            if (types.isEmpty()) throw new IllegalArgumentException("Empty mob types");
            total = total.checked(v -> v == -1 || v > 0, "Invalid total");
            interval = interval.checked(v -> v > 0, "Invalid interval_ticks");
            waveSize = waveSize.checked(v -> v > 0 && v <= 4096, "Invalid wave_size");
            maxAlive = maxAlive.checked(v -> v > 0 && v <= 4096, "Invalid max_alive");
            hpMultiplier = hpMultiplier.checked(v -> v > 0 && v <= 1000, "Invalid health_multiplier");
            healthMin = healthMin.checked(v -> v >= 0 && v <= 100000, "Invalid health_min");
            healthMax = healthMax.checked(v -> v >= 0 && v <= 100000, "Invalid health_max");
            liftTicks = liftTicks.checked(v -> v >= 0 && v <= 1200, "Invalid lift_ticks");
            horizontalSpeed = horizontalSpeed.checked(v -> v >= 0 && v <= 10, "Invalid horizontal_speed");
            verticalSpeed = verticalSpeed.checked(v -> v >= 0 && v <= 10, "Invalid vertical_speed");
            rolls = rolls.checked(v -> v >= 0 && v <= 4096, "Invalid rolls");
        }

        public AirdropComponent create(Airdrop air, String name) {
            return new MobsComponent(this, name, air);
        }

        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
