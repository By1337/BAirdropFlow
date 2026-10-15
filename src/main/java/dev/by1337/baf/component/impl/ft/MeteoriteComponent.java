package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.DoubleValue;
import dev.by1337.baf.config.value.IntValue;
import dev.by1337.baf.config.value.StringValue;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class MeteoriteComponent extends FtComponent {
    private static final double[][] OFFSETS = {{0, 0, 0}, {0, .622, 0}, {.622, .622, 0}, {-.622, .622, 0}, {0, .622, .622}, {0, 1.244, 0}, {0, 1.244, .622}, {.622, .622, .622}, {-.622, .622, -.622}};
    private static final Material[] BODY = {Material.CRYING_OBSIDIAN, Material.BLACKSTONE, Material.NETHER_GOLD_ORE, Material.GILDED_BLACKSTONE,
            Material.MAGMA_BLOCK, Material.CRYING_OBSIDIAN, Material.GILDED_BLACKSTONE, Material.MAGMA_BLOCK, Material.MAGMA_BLOCK};

    private record Flight(List<ArmorStand> parts, Location from, Location target, int born) {}

    private record Debris(ArmorStand entity, Vector velocity, int expires) {}

    private static final class Crater {
        final Location at;
        final BlockData before;
        final int born;
        BlockData applied;

        Crater(Location at, int born) {
            this.at = at;
            this.born = born;
            before = at.getBlock().getBlockData().clone();
        }
    }

    private final Config config;
    private final List<Flight> flights = new ArrayList<>();
    private final List<Debris> debris = new ArrayList<>();
    private final List<Crater> craters = new ArrayList<>();
    private int interval, flightTicks, nextWave;
    private double radius, height;

    private MeteoriteComponent(Config c, String name, Airdrop air) {
        super(c.common, name, air);
        config = c;
    }

    @Override
    protected void start(EventContext ctx) {
        interval = config.interval.getInt(airdrop);
        flightTicks = config.flightTicks.getInt(airdrop);
        radius = config.radius.getDouble(airdrop);
        height = config.height.getDouble(airdrop);
    }

    @Override
    protected void tick(EventContext ctx) {
        if (age >= nextWave) {
            nextWave = age + interval;
            int min = config.waveMin.getInt(airdrop), max = config.waveMax.getInt(airdrop);
            if (min > max) throw new IllegalArgumentException("Invalid wave range");
            int count = randomInt(min, max);
            for (int i = 0; i < count; i++) launch(i);
        }
        for (var flight : List.copyOf(flights)) {
            if (flight.parts.stream().anyMatch(p -> !entities.owns(p))) {
                cancel(flight);
                continue;
            }
            int elapsed = age - flight.born;
            if (elapsed >= flightTicks) {
                cancel(flight);
                impact(flight.target);
                continue;
            }
            double progress = (double) elapsed / flightTicks;
            progress *= progress;
            Location at = flight.from.clone().add(flight.target.toVector().subtract(flight.from.toVector()).multiply(progress));
            boolean loaded = true;
            for (int i = 0; i < flight.parts.size(); i++) {
                double[] offset = OFFSETS[i];
                Location position = at.clone().add(offset[0], offset[1], offset[2]);
                if (!FtEntities.loaded(position)) {
                    loaded = false;
                    break;
                }
                flight.parts.get(i).teleport(position);
            }
            if (!loaded) cancel(flight);
        }
        for (var part : List.copyOf(debris)) {
            if (!entities.owns(part.entity) || age >= part.expires) {
                entities.remove(part.entity);
                debris.remove(part);
                continue;
            }
            Location at = part.entity.getLocation().add(part.velocity);
            if (FtEntities.loaded(at)) part.entity.teleport(at);
            else {
                entities.remove(part.entity);
                debris.remove(part);
            }
        }
        for (var crater : List.copyOf(craters)) {
            if (!FtEntities.loaded(crater.at)) {
                craters.remove(crater);
                continue;
            }
            int elapsed = age - crater.born;
            if (elapsed >= 80) {
                restore(crater);
                craters.remove(crater);
                if (random(0, 100) < config.dropChance.getDouble(airdrop))
                    loot(crater.at.clone().add(.5, .5, .5), config.lootTable.getString(airdrop), 1, new Vector(0, .1, 0));
            } else if (elapsed == 20) set(crater, Material.GILDED_BLACKSTONE);
            else if (elapsed == 40) set(crater, Material.NETHER_GOLD_ORE);
            else if (elapsed == 60) {
                set(crater, Material.MAGMA_BLOCK);
                fragments(crater.at, Material.MAGMA_BLOCK, 20, .08);
            }
        }
    }

    private void launch(int index) {
        if (entities.size() + 9 > settings.maxEntities().getInt(airdrop)) return;
        Location target = origin.clone().add(random(-radius, radius), 0, (index % 2 == 0 ? -5 : 5) + random(-2, 2));
        if (!FtEntities.loaded(target)) return;
        target.setY(target.getWorld().getHighestBlockYAt(target.getBlockX(), target.getBlockZ()) + 1);
        Location from = target.clone().add(15, height, 0);
        if (!FtEntities.loaded(from)) return;
        List<ArmorStand> parts = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            double[] off = OFFSETS[i];
            ArmorStand part = model(from.clone().add(off[0], off[1], off[2]), BODY[i]);
            if (part == null) {
                parts.forEach(entities::remove);
                return;
            }
            parts.add(part);
        }
        flights.add(new Flight(parts, from, target, age));
    }

    private ArmorStand model(Location at, Material material) {
        return spawn(at, ArmorStand.class, false, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvulnerable(true);
            stand.setBasePlate(false);
            stand.setSilent(true);
            stand.getEquipment().setHelmet(new ItemStack(material));
        });
    }

    private void cancel(Flight flight) {
        flight.parts.forEach(entities::remove);
        flights.remove(flight);
    }

    private void impact(Location at) {
        if (!FtEntities.loaded(at)) return;
        particle(at, Particle.EXPLOSION, 10, .5);
        at.getWorld().playSound(at, "entity.generic.explode", 2, 1);
        radialDamage(at, config.impactRadius.getDouble(airdrop), config.damage.getDouble(airdrop));
        fragments(at.clone().subtract(0, 1, 0), Material.WHITE_STAINED_GLASS, 40, .075);
        Crater crater = new Crater(at.getBlock().getLocation(), age);
        // Repeated impacts at one location share a single restoration owner.
        for (Crater previous : List.copyOf(craters))
            if (previous.at.equals(crater.at)) {
                restore(previous);
                craters.remove(previous);
                crater = new Crater(crater.at, age);
            }
        set(crater, Material.BLACKSTONE);
        craters.add(crater);
        emit(settings.impact(), at, null, null);
    }

    private void fragments(Location at, Material material, int ticks, double speed) {
        for (int i = 0; i < 8; i++) {
            double angle = random(0, Math.PI * 2);
            var part = model(at, material);
            if (part != null)
                debris.add(new Debris(part, new Vector(Math.cos(angle) * speed, material == Material.MAGMA_BLOCK ? .04 : 0, Math.sin(angle) * speed), age + ticks));
        }
    }

    private void set(Crater crater, Material material) {
        if (crater.applied != null && !crater.at.getBlock().getBlockData().equals(crater.applied)) return;
        crater.applied = material.createBlockData();
        crater.at.getBlock().setBlockData(crater.applied, false);
    }

    private void restore(Crater crater) {
        if (FtEntities.loaded(crater.at) && crater.applied != null && crater.at.getBlock().getBlockData().equals(crater.applied))
            crater.at.getBlock().setBlockData(crater.before, false);
    }

    @Override
    public void chunkUnloading(Chunk chunk) {
        for (Crater crater : List.copyOf(craters))
            if (crater.at.getWorld() == chunk.getWorld() && (crater.at.getBlockX() >> 4) == chunk.getX() && (crater.at.getBlockZ() >> 4) == chunk.getZ()) {
                restore(crater);
                craters.remove(crater);
            }
    }

    @Override
    protected void removed() {
        craters.forEach(this::restore);
        craters.clear();
        flights.clear();
        debris.clear();
    }

    public record Config(FtSettings common, IntValue interval, IntValue flightTicks, IntValue waveMin, IntValue waveMax,
                         DoubleValue radius, DoubleValue height, DoubleValue impactRadius, DoubleValue damage,
                         StringValue lootTable, DoubleValue dropChance) implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = FtSettings.schema("FT: метеориты", "Составная модель, полёт progress², удар и временные стадии блока с восстановлением.")
                .integer("interval_ticks", "Интервал волн", 100, 1, false).integer("flight_ticks", "Время полёта", 55, 1, false)
                .integer("wave_min", "Минимум в волне", 1, 1, false).integer("wave_max", "Максимум в волне", 2, 1, false)
                .number("radius", "Разброс X", 15, 0, 128D).number("height", "Высота", 70, 1, 256D)
                .number("impact_radius", "Радиус урона", 10, 0, 128D).number("max_damage", "Максимальный урон", 20, 0, 2048D)
                .text("loot_table", "Лут после остывания (пусто — без лута)", "", false).number("drop_chance", "Шанс лута, %", 70, 0, 100D);
        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new, FtSettings.DECODER.fieldOf(),
                IntValue.DECODER.fieldOf("interval_ticks", new IntValue(100)), IntValue.DECODER.fieldOf("flight_ticks", new IntValue(55)),
                IntValue.DECODER.fieldOf("wave_min", new IntValue(1)), IntValue.DECODER.fieldOf("wave_max", new IntValue(2)),
                DoubleValue.DECODER.fieldOf("radius", new DoubleValue(15)), DoubleValue.DECODER.fieldOf("height", new DoubleValue(70)),
                DoubleValue.DECODER.fieldOf("impact_radius", new DoubleValue(10)), DoubleValue.DECODER.fieldOf("max_damage", new DoubleValue(20)),
                StringValue.DECODER.fieldOf("loot_table", new StringValue("")), DoubleValue.DECODER.fieldOf("drop_chance", new DoubleValue(70)));

        public Config {
            interval = interval.checked(v -> v > 0, "Invalid interval_ticks");
            flightTicks = flightTicks.checked(v -> v > 0 && v <= 12000, "Invalid flight_ticks");
            waveMin = waveMin.checked(v -> v > 0 && v <= 64, "Invalid wave_min");
            waveMax = waveMax.checked(v -> v > 0 && v <= 64, "Invalid wave_max");
            radius = radius.checked(v -> v >= 0 && v <= 128, "Invalid radius");
            height = height.checked(v -> v > 0 && v <= 256, "Invalid height");
            impactRadius = impactRadius.checked(v -> v >= 0 && v <= 128, "Invalid impact_radius");
            damage = damage.checked(v -> v >= 0 && v <= 2048, "Invalid damage");
            dropChance = dropChance.checked(v -> v >= 0 && v <= 100, "Invalid drop_chance");
        }

        public AirdropComponent create(Airdrop air, String name) {
            return new MeteoriteComponent(this, name, air);
        }

        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
