package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.BukkitYamlDecoders;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class StrangeBeaconComponent extends FtComponent {
    public record Stage(Material material, int ticks) {
        static final YamlDecoder<Stage> DECODER = RecordYamlDecoder.mapOf(Stage::new,
                BukkitYamlDecoders.material().fieldOf("material"), YamlDecoder.INT.fieldOf("ticks"));

        public Stage {
            if (material.isAir() || !material.isItem() || ticks < 1 || ticks > 12000)
                throw new IllegalArgumentException("Invalid animation stage");
        }
    }

    public record Rays(double radius, int count, double step, double speed, double jitter, double rotation) {
        static final YamlDecoder<Rays> DECODER = RecordYamlDecoder.mapOf(Rays::new,
                YamlDecoder.DOUBLE.fieldOf("radius", 9D), YamlDecoder.INT.fieldOf("count", 8),
                YamlDecoder.DOUBLE.fieldOf("step", .6D), YamlDecoder.DOUBLE.fieldOf("speed", .225D),
                YamlDecoder.DOUBLE.fieldOf("jitter", .15D), YamlDecoder.DOUBLE.fieldOf("rotation", 0D));

        public Rays {
            if (!Double.isFinite(radius) || radius <= 0 || radius > 64 || count < 1 || count > 64
                    || !Double.isFinite(step) || step < .1 || step > radius
                    || !Double.isFinite(speed) || speed < .01 || speed > 64
                    || !Double.isFinite(jitter) || jitter < 0 || jitter > 2 || !Double.isFinite(rotation)
                    || count * Math.ceil(radius / step) > 4095)
                throw new IllegalArgumentException("Invalid ray geometry (maximum 4096 points)");
        }
    }

    public record Ground(int searchUp, int searchDown, double offset, double verticalStep) {
        static final YamlDecoder<Ground> DECODER = RecordYamlDecoder.mapOf(Ground::new,
                YamlDecoder.INT.fieldOf("search_up", 8), YamlDecoder.INT.fieldOf("search_down", 8),
                YamlDecoder.DOUBLE.fieldOf("offset", -.3D), YamlDecoder.DOUBLE.fieldOf("vertical_step", .3D));

        public Ground {
            if (searchUp < 0 || searchUp > 32 || searchDown < 0 || searchDown > 32
                    || !Double.isFinite(offset) || Math.abs(offset) > 4
                    || !Double.isFinite(verticalStep) || verticalStep < .1 || verticalStep > 2)
                throw new IllegalArgumentException("Invalid ground following settings");
        }
    }

    private record Step(Location at, int tick) {}

    private record Stand(ArmorStand entity, int born) {}

    private final Config config;
    private final List<Step> steps = new ArrayList<>();
    private final List<Stand> stands = new ArrayList<>();
    private int next;
    private int stageTicks;

    private StrangeBeaconComponent(Config config, String name, Airdrop air) {
        super(config.common, name, air);
        this.config = config;
    }

    @Override
    protected void start(EventContext ctx) {
        stageTicks = config.stages.stream().mapToInt(Stage::ticks).sum();
        int center = floor(origin.getX(), origin.getBlockY(), origin.getZ());
        if (center == Integer.MIN_VALUE) center = origin.getBlockY();
        steps.add(new Step(new Location(origin.getWorld(), origin.getX(), center + config.ground.offset, origin.getZ()), 0));
        Rays rays = config.rays;
        for (int ray = 0; ray < rays.count; ray++) {
            double angle = Math.toRadians(rays.rotation + 360D * ray / rays.count);
            int previous = center;
            double previousX = origin.getX(), previousZ = origin.getZ();
            int samples = (int) Math.ceil(rays.radius / rays.step);
            for (int sample = 1; sample <= samples && steps.size() < 4096; sample++) {
                double distance = Math.min(rays.radius, sample * rays.step);
                double x = origin.getX() + Math.cos(angle) * distance + random(-rays.jitter, rays.jitter);
                double z = origin.getZ() + Math.sin(angle) * distance + random(-rays.jitter, rays.jitter);
                int ground = floor(x, previous, z);
                if (ground == Integer.MIN_VALUE) continue;
                int tick = (int) Math.round(distance / rays.speed);
                if (Math.abs(ground - previous) > 1) {
                    double edgeX = ground < previous ? x : previousX;
                    double edgeZ = ground < previous ? z : previousZ;
                    for (double y = Math.min(previous, ground) + config.ground.verticalStep;
                         y < Math.max(previous, ground) && steps.size() < 4095; y += config.ground.verticalStep)
                        steps.add(new Step(new Location(origin.getWorld(), edgeX, y + config.ground.offset, edgeZ), tick));
                }
                steps.add(new Step(new Location(origin.getWorld(), x, ground + config.ground.offset, z), tick));
                previous = ground;
                previousX = x;
                previousZ = z;
            }
        }
        steps.sort(Comparator.comparingInt(Step::tick));
    }

    private int floor(double x, int y, double z) {
        Location at = new Location(origin.getWorld(), x, y, z);
        if (!FtEntities.loaded(at)) return Integer.MIN_VALUE;
        for (int d = 0; d <= config.ground.searchDown; d++)
            if (solid(at.getBlockX(), y - d, at.getBlockZ())) return y - d;
        for (int d = 1; d <= config.ground.searchUp; d++)
            if (solid(at.getBlockX(), y + d, at.getBlockZ())) return y + d;
        return Integer.MIN_VALUE;
    }

    private boolean solid(int x, int y, int z) {
        return y >= origin.getWorld().getMinHeight() && y + 1 < origin.getWorld().getMaxHeight()
                && origin.getWorld().getBlockAt(x, y, z).getType().isSolid()
                && !origin.getWorld().getBlockAt(x, y + 1, z).getType().isSolid();
    }

    @Override
    protected void tick(EventContext ctx) {
        if (age == config.buildStartTick) {
            emit(config.buildStart, origin, null, null);
            if (isRemoved()) return;
        }
        // Retire old stands before applying the simultaneous entity limit to new ones.
        for (Stand stand : List.copyOf(stands)) {
            int time = age - stand.born;
            if (!entities.owns(stand.entity) || time >= stageTicks) {
                entities.remove(stand.entity);
                stands.remove(stand);
                continue;
            }
            for (Stage stage : config.stages) {
                if (time < stage.ticks) {
                    var equipment = stand.entity.getEquipment();
                    if (equipment.getHelmet() == null || equipment.getHelmet().getType() != stage.material)
                        equipment.setHelmet(new ItemStack(stage.material));
                    break;
                }
                time -= stage.ticks;
            }
        }
        while (next < steps.size() && steps.get(next).tick <= age) {
            Step step = steps.get(next++);
            ArmorStand stand = spawn(step.at, ArmorStand.class, false, s -> {
                s.setVisible(false);
                s.setGravity(false);
                s.setMarker(true);
                s.setBasePlate(false);
                s.setArms(false);
                s.setCollidable(false);
                s.setInvulnerable(true);
                s.setSilent(true);
                s.getEquipment().setHelmet(new ItemStack(config.stages.getFirst().material));
            });
            if (stand != null) stands.add(new Stand(stand, age));
        }
        if (age >= config.buildStartTick && next == steps.size() && stands.isEmpty()) finish(ctx);
    }

    @Override
    protected void removed() {
        steps.clear();
        stands.clear();
    }

    public record Config(FtSettings common, Rays rays, Ground ground, List<Stage> stages,
                         int buildStartTick, EventHandler buildStart) implements ComponentFactory {
        private static final List<Stage> DEFAULT_STAGES = List.of(new Stage(Material.FIRE_CORAL_BLOCK, 40),
                new Stage(Material.HORN_CORAL_BLOCK, 3), new Stage(Material.FIRE_CORAL_BLOCK, 40),
                new Stage(Material.BLACK_CONCRETE_POWDER, 12));
        public static final NodeSchema WEB_SCHEMA = FtSettings.schema("FT: Загадочный маяк", "Процедурные лучи из несохраняемых стоек, повторяющие рельеф.")
                .object("rays", "Лучи", NodeSchema.of("Лучи", "")
                        .number("radius", "Радиус", 9, .1, 64D).integer("count", "Количество", 8, 1, false)
                        .number("step", "Шаг", .6, .1, 64D).number("speed", "Блоков за тик", .225, .01, 64D)
                        .number("jitter", "Разброс", .15, 0, 2D).number("rotation", "Поворот", 0, -360, 360D))
                .object("ground", "Рельеф", NodeSchema.of("Рельеф", "")
                        .integer("search_up", "Поиск вверх", 8, 0, false).integer("search_down", "Поиск вниз", 8, 0, false)
                        .number("offset", "Высота стойки над блоком", -.3, -4, 4D).number("vertical_step", "Шаг на перепаде", .3, .1, 2D))
                .objects("stages", "Стадии смены предмета", NodeSchema.of("Стадия", "")
                                .text("material", "Предмет", "fire_coral_block", true).integer("ticks", "Длительность", 40, 1, true),
                        DEFAULT_STAGES.stream().map(s -> Map.of("material", s.material.name(), "ticks", s.ticks)).toList())
                .integer("build_start_tick", "Начать постройку на тике", 81, 0, false).events("on_build_start");
        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new,
                FtSettings.DECODER.fieldOf(), Rays.DECODER.fieldOf("rays", new Rays(9, 8, .6, .225, .15, 0)),
                Ground.DECODER.fieldOf("ground", new Ground(8, 8, -.3, .3)),
                Stage.DECODER.listOf().fieldOf("stages", DEFAULT_STAGES), YamlDecoder.INT.fieldOf("build_start_tick", 81),
                HANDLER.fieldOf("on_build_start", EMPTY_HANDLER));

        public Config {
            stages = List.copyOf(stages);
            if (stages.isEmpty() || stages.size() > 32 || stages.stream().mapToInt(Stage::ticks).sum() > 12000
                    || buildStartTick < 0 || buildStartTick > 12000)
                throw new IllegalArgumentException("Invalid animation timing");
        }

        public AirdropComponent create(Airdrop air, String name) {
            return new StrangeBeaconComponent(this, name, air);
        }

        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
