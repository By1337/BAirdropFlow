package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.config.value.DoubleValue;
import dev.by1337.baf.config.value.IntValue;
import dev.by1337.baf.config.value.MaterialValue;
import dev.by1337.baf.config.value.StringValue;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.util.Vector;

import java.util.*;

public final class GeyserComponent extends FtComponent {
    private final Config config;
    private final List<Location> sources = new ArrayList<>();
    private final Map<Location, Eruption> active = new HashMap<>();
    private int next, burstAt = -1;
    private boolean bursting;
    private dev.by1337.bmenu.random.WeightedItemSelector<Material> fallingMaterials;

    private static final class Eruption {
        final int born, count;
        int dropped;

        Eruption(int born, int count) {
            this.born = born;
            this.count = count;
        }
    }

    private GeyserComponent(Config c, String name, Airdrop air) {
        super(c.common, name, air);
        config = c;
        exportEvent("erupt", this::erupt);
    }

    @Override
    protected void start(EventContext ctx) {
        var weights = config.materials.stream().map(value -> {
            Material material = value.material.getMaterial(airdrop);
            if (!material.isBlock() || material.isAir())
                throw new IllegalArgumentException("Invalid falling material " + material);
            return dev.by1337.bmenu.random.WeightedItem.of(value.weight.getDouble(airdrop), material);
        }).toList();
        fallingMaterials = new dev.by1337.bmenu.random.WeightedItemSelector<>(weights);
        int radius = config.scanRadius.getInt(airdrop), vertical = config.scanY.getInt(airdrop);
        for (int x = -radius; x <= radius; x++)
            for (int z = -radius; z <= radius; z++) {
                Location column = origin.clone().add(x, 0, z);
                if (!FtEntities.loaded(column)) continue;
                for (int y = -vertical; y <= vertical; y++) {
                    int by = origin.getBlockY() + y;
                    if (by < origin.getWorld().getMinHeight() || by >= origin.getWorld().getMaxHeight()) continue;
                    var block = origin.getWorld().getBlockAt(column.getBlockX(), by, column.getBlockZ());
                    if (block.getType() == Material.NETHERRACK) sources.add(block.getLocation());
                }
            }
        Collections.shuffle(sources);
    }

    @Override
    protected void tick(EventContext ctx) {
        if (bursting) {
            if (age - burstAt >= 2 && entities.size() == 0 && dropsFinished()) finish(ctx);
            return;
        }
        if (age >= next) {
            next = age + config.interval.getInt(airdrop);
            if (active.size() < config.concurrent.getInt(airdrop)) for (int i = 0; i < sources.size(); i++) {
                Location at = sources.removeFirst();
                sources.add(at);
                if (!active.containsKey(at) && FtEntities.loaded(at) && at.getBlock().getType() == Material.NETHERRACK) {
                    active.put(at, new Eruption(age, randomInt(1, 2)));
                    break;
                }
            }
        }
        for (var entry : List.copyOf(active.entrySet())) {
            Location block = entry.getKey(), at = block.clone().add(.5, 1, .5);
            Eruption effect = entry.getValue();
            int elapsed = age - effect.born;
            if (!FtEntities.loaded(at)) {
                active.remove(block);
                continue;
            }
            if (elapsed % 4 == 0) particle(at, Particle.LAVA, 2, .2);
            if (elapsed % 5 == 0) particle(at, Particle.LARGE_SMOKE, 2, .2);
            if (effect.dropped < effect.count && elapsed >= (effect.dropped + 1) * config.itemDelay.getInt(airdrop)) {
                effect.dropped++;
                scatter(at, config.lootTable.getString(airdrop), 1, config.masks.stream().map(v -> v.getMaterial(airdrop)).toList(), config.maskName.getString(airdrop),
                        item -> {
                            item.setVelocity(new Vector(random(-.1, .1), random(.25, .4), random(-.1, .1)));
                            item.setInvulnerable(true);
                            if (item.getItemStack().hasItemMeta())
                                item.customName(item.getItemStack().getItemMeta().displayName());
                            item.setCustomNameVisible(true);
                        });
            }
            if (elapsed >= config.effectTicks.getInt(airdrop) && effect.dropped >= effect.count) active.remove(block);
        }
    }

    private void erupt(EventContext ctx) {
        if (bursting || entities == null || isRemoved()) return;
        bursting = true;
        burstAt = age;
        active.clear();
        Location at = origin.clone().add(.5, 1, .5);
        if (!FtEntities.loaded(at)) return;
        particle(at, Particle.EXPLOSION_EMITTER, 1, 0);
        at.getWorld().playSound(at, "entity.generic.explode", 10, .6f);
        radialDamage(at, config.damageRadius.getDouble(airdrop), config.damage.getDouble(airdrop));
        for (int i = 0; i < config.blocks.getInt(airdrop); i++) {
            double angle = random(0, Math.PI * 2), speed = random(0, .5);
            Material material = fallingMaterials.getRandomItem();
            spawn(at.clone().add(random(-1.5, 1.5), random(-.5, .5), random(-1.5, 1.5)), FallingBlock.class, false, fb -> {
                fb.setBlockData(material.createBlockData());
                fb.setDropItem(false);
                fb.setHurtEntities(false);
                fb.setCancelDrop(true);
                fb.setVelocity(new Vector(Math.cos(angle) * speed, random(-.6, .6), Math.sin(angle) * speed));
            });
        }
        emit(settings.impact(), at, null, null);
    }

    @Override
    public void landed(Entity entity) {
        if (FtEntities.loaded(entity.getLocation())) {
            particle(entity.getLocation(), Particle.LAVA, 3, .1);
            entity.getWorld().playSound(entity.getLocation(), "block.netherrack.fall", .5f, .75f);
        }
        super.landed(entity);
    }

    @Override
    protected void removed() {
        active.clear();
        sources.clear();
    }

    public record WeightedMaterial(MaterialValue material, DoubleValue weight) {
        public static final YamlDecoder<WeightedMaterial> DECODER = RecordYamlDecoder.mapOf(WeightedMaterial::new,
                MaterialValue.DECODER.fieldOf("material"), DoubleValue.DECODER.fieldOf("weight", new DoubleValue(1)));

        public WeightedMaterial {
            weight = weight.checked(v -> v > 0 && v <= 1000000, "Invalid material weight");
        }
    }

    public record Config(FtSettings common, IntValue scanRadius, IntValue scanY, IntValue interval, IntValue concurrent,
                         IntValue effectTicks, IntValue itemDelay, StringValue lootTable, List<MaterialValue> masks,
                         StringValue maskName,
                         IntValue blocks, DoubleValue damageRadius, DoubleValue damage,
                         List<WeightedMaterial> materials) implements ComponentFactory {
        public static final NodeSchema WEB_SCHEMA = FtSettings.schema("FT: гейзер", "Ищет NETHERRACK без загрузки чанков. Вход erupt запускает финал; remove только очищает.")
                .integer("scan_radius", "Радиус поиска XZ", 15, 0, false).integer("scan_y", "Радиус поиска Y", 10, 0, false)
                .integer("interval_ticks", "Интервал источников", 4, 1, false).integer("max_concurrent", "Одновременные источники", 3, 1, false)
                .integer("effect_ticks", "Длительность источника", 60, 1, false).integer("item_delay_ticks", "Интервал предметов", 2, 1, false)
                .text("loot_table", "Лут (пусто — без лута)", "", false).materials("obfuscation_materials", "Маски")
                .text("obfuscation_name", "Имя масок", "<gold>Богатое сокровище", false)
                .integer("falling_blocks", "Блоков в финале", 50, 0, false).number("damage_radius", "Радиус урона", 8, 0, 128D)
                .number("max_damage", "Максимальный урон", 10, 0, 2048D)
                .objects("falling_materials", "Материалы финала", NodeSchema.of("Материал", "")
                        .text("material", "Материал", "stone", true).number("weight", "Вес", 1, .01, 1000000D)).inputs("erupt");
        public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new, FtSettings.DECODER.fieldOf(),
                IntValue.DECODER.fieldOf("scan_radius", new IntValue(15)), IntValue.DECODER.fieldOf("scan_y", new IntValue(10)),
                IntValue.DECODER.fieldOf("interval_ticks", new IntValue(4)), IntValue.DECODER.fieldOf("max_concurrent", new IntValue(3)),
                IntValue.DECODER.fieldOf("effect_ticks", new IntValue(60)), IntValue.DECODER.fieldOf("item_delay_ticks", new IntValue(2)),
                StringValue.DECODER.fieldOf("loot_table", new StringValue("")), MaterialValue.DECODER.listOf().fieldOf("obfuscation_materials", List.of(new MaterialValue(Material.NAUTILUS_SHELL))),
                StringValue.DECODER.fieldOf("obfuscation_name", new StringValue("<gold>Богатое сокровище")), IntValue.DECODER.fieldOf("falling_blocks", new IntValue(50)),
                DoubleValue.DECODER.fieldOf("damage_radius", new DoubleValue(8)), DoubleValue.DECODER.fieldOf("max_damage", new DoubleValue(10)),
                WeightedMaterial.DECODER.listOf().fieldOf("falling_materials", List.of(
                        new WeightedMaterial(new MaterialValue(Material.STONE), new DoubleValue(49)),
                        new WeightedMaterial(new MaterialValue(Material.MAGMA_BLOCK), new DoubleValue(28)),
                        new WeightedMaterial(new MaterialValue(Material.COBBLESTONE), new DoubleValue(24)))));

        public Config {
            materials = List.copyOf(materials);
            if (materials.isEmpty()) materials = List.of(
                    new WeightedMaterial(new MaterialValue(Material.STONE), new DoubleValue(49)),
                    new WeightedMaterial(new MaterialValue(Material.MAGMA_BLOCK), new DoubleValue(28)),
                    new WeightedMaterial(new MaterialValue(Material.COBBLESTONE), new DoubleValue(24)));
            if (materials.size() > 256) throw new IllegalArgumentException("Invalid falling_materials");
            scanRadius = scanRadius.checked(v -> v >= 0 && v <= 32, "Invalid scan_radius");
            scanY = scanY.checked(v -> v >= 0 && v <= 32, "Invalid scan_y");
            interval = interval.checked(v -> v > 0, "Invalid interval_ticks");
            concurrent = concurrent.checked(v -> v > 0 && v <= 64, "Invalid max_concurrent");
            effectTicks = effectTicks.checked(v -> v > 0 && v <= 12000, "Invalid effect_ticks");
            itemDelay = itemDelay.checked(v -> v > 0, "Invalid item_delay_ticks");
            blocks = blocks.checked(v -> v >= 0 && v <= 4096, "Invalid falling_blocks");
            masks = List.copyOf(masks);
            damageRadius = damageRadius.checked(v -> v >= 0 && v <= 128, "Invalid damage_radius");
            damage = damage.checked(v -> v >= 0 && v <= 2048, "Invalid max_damage");
        }

        public AirdropComponent create(Airdrop air, String name) {
            return new GeyserComponent(this, name, air);
        }

        public YamlDecoder<? extends ComponentFactory> codec() {
            return DECODER;
        }
    }
}
