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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FallingBlock;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FallingBlocksComponent extends FtComponent {
    private final Config config;
    private List<Step> steps;
    private int next;
    private int end;

    private record Step(int tick, Location at, Material material, Vector velocity) {}

    private FallingBlocksComponent(Config c, String name, Airdrop air) {
        super(c.common, name, air);
        config = c;
    }

    @Override
    protected void start(EventContext ctx) {
        steps = new ArrayList<>();
        int count = config.count.getInt(airdrop);
        if (count > settings.maxEntities().getInt(airdrop))
            throw new IllegalArgumentException("count exceeds max_entities");
        var min = config.velocityMin.getVector(airdrop);
        var max = config.velocityMax.getVector(airdrop);
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z())
            throw new IllegalArgumentException("Invalid velocity range");
        List<Material> materials = config.materials.stream().map(m -> m.getMaterial(airdrop)).toList();
        for (Material material : materials)
            if (!material.isBlock() || material.isAir())
                throw new IllegalArgumentException("Expected solid block material");
        double spread = config.spread.getDouble(airdrop);
        if (config.steps.isEmpty()) {
            for (int i = 0; i < count; i++) {
                Location at = origin.clone().add(random(-spread, spread), 0, random(-spread, spread));
                Material material = materials.get(randomInt(0, materials.size() - 1));
                if (config.groundMaterial.getBoolean(airdrop) && FtEntities.loaded(origin)) {
                    Material ground = origin.clone().subtract(0, 1, 0).getBlock().getType();
                    if (ground.isBlock() && !ground.isAir()) material = ground;
                }
                steps.add(new Step(0, at, material, new Vector(random(min.x(), max.x()), random(min.y(), max.y()), random(min.z(), max.z()))));
            }
        } else for (var step : config.steps) {
            var offset = step.offsets.getVector(airdrop);
            var velocity = step.velocity.getVector(airdrop);
            Material material = step.material.getMaterial(airdrop);
            if (!material.isBlock() || material.isAir()) throw new IllegalArgumentException("Expected block material");
            if (config.groundMaterial.getBoolean(airdrop) && FtEntities.loaded(origin)) {
                Material ground = origin.clone().subtract(0, 1, 0).getBlock().getType();
                if (ground.isBlock() && !ground.isAir()) material = ground;
            }
            steps.add(new Step(step.tick.getInt(airdrop), origin.clone().add(offset.x(), offset.y(), offset.z()), material,
                    new Vector(velocity.x(), velocity.y(), velocity.z())));
        }
        steps.sort(Comparator.comparingInt(Step::tick));
        end = steps.isEmpty() ? 0 : steps.getLast().tick;
    }

    @Override
    protected void tick(EventContext ctx) {
        while (next < steps.size() && steps.get(next).tick <= age) {
            var step = steps.get(next++);
            spawn(step.at, FallingBlock.class, false, block -> {
                block.setBlockData(step.material.createBlockData());
                block.setDropItem(false);
                block.setHurtEntities(false);
                block.setCancelDrop(true);
                block.setVelocity(step.velocity);
            });
        }
        if (age >= end && next == steps.size() && entities.size() == 0) finish(ctx);
    }

    public record TimedBlock(IntValue tick, Vec3dValue offsets, MaterialValue material, Vec3dValue velocity) {
        public static final YamlDecoder<TimedBlock> DECODER = RecordYamlDecoder.mapOf(TimedBlock::new,
                IntValue.DECODER.fieldOf("tick", new IntValue(0)),
                Vec3dValue.DECODER.fieldOf("offsets", new Vec3dValue(new Vec3d(0, 0, 0))),
                MaterialValue.DECODER.fieldOf("material", new MaterialValue(Material.STONE)),
                Vec3dValue.DECODER.fieldOf("velocity", new Vec3dValue(new Vec3d(0, 0, 0))));

        public TimedBlock {
            tick = tick.checked(v -> v >= 0 && v <= 1200000, "Invalid step tick");
        }
    }

    public record Config(FtSettings common, List<MaterialValue> materials, IntValue count, DoubleValue spread,
                         Vec3dValue velocityMin, Vec3dValue velocityMax, BooleanValue groundMaterial,
                         List<TimedBlock> steps) implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = FtSettings.schema("FT: падающие блоки", "Физические блоки без постановки и дропа; remove очищает всю группу.")
                .materials("materials", "Материалы", List.of("stone")).integer("count", "Количество", 1, 0, false)
                .number("spread", "Разброс по X/Z", 0, 0, 128D)
                .vector("velocity_min", "Минимальная скорость", "0;0;0", true)
                .vector("velocity_max", "Максимальная скорость", "0;0;0", true)
                .bool("ground_material", "Брать материал опоры", false)
                .objects("steps", "Временные шаги", NodeSchema.of("Шаг", "")
                        .integer("tick", "Тик появления", 0, 0, false).vector("offsets", "Смещение", "0;0;0", true)
                        .text("material", "Материал", "stone", false).vector("velocity", "Скорость", "0;0;0", true));
        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new,
                FtSettings.DECODER.fieldOf(), MaterialValue.DECODER.listOf().fieldOf("materials", List.of(new MaterialValue(Material.STONE))),
                IntValue.DECODER.fieldOf("count", new IntValue(1)), DoubleValue.DECODER.fieldOf("spread", new DoubleValue(0)),
                Vec3dValue.DECODER.fieldOf("velocity_min", new Vec3dValue(new Vec3d(0, 0, 0))),
                Vec3dValue.DECODER.fieldOf("velocity_max", new Vec3dValue(new Vec3d(0, 0, 0))),
                BooleanValue.DECODER.fieldOf("ground_material", new BooleanValue(false)),
                TimedBlock.DECODER.listOf().fieldOf("steps", List.of()));

        public Config {
            materials = List.copyOf(materials);
            steps = List.copyOf(steps);
            if (materials.isEmpty() || steps.size() > 4096)
                throw new IllegalArgumentException("Empty materials or too many steps");
            count = count.checked(v -> v >= 0 && v <= 4096, "Invalid count");
            spread = spread.checked(v -> v >= 0 && v <= 128, "Invalid spread");
        }

        public AirdropComponent create(Airdrop air, String name) {
            return new FallingBlocksComponent(this, name, air);
        }

        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
