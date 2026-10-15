package dev.by1337.baf;

import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.baf.entity.TickableEntity;
import dev.by1337.baf.generator.Generator;
import dev.by1337.baf.generator.impl.FixedGenerator;
import dev.by1337.baf.spawner.AirdropSpawner;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.plc.Placeholders;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;

import java.util.Map;
import java.util.Objects;

public class Workflow {
    public static final YamlDecoder<Workflow> CODEC = RecordYamlDecoder.mapOf(
            Workflow::new,
            YamlDecoder.STRING.fieldOf("id"),
            YamlDecoder.mapOf(YamlDecoder.STRING, AirdropComponent.REGISTRY.decoder)
                    .fieldOf("nodes"),
            Generator.REGISTRY.decoder.fieldOf("generator", new FixedGenerator(new Vec3i(0, 100, 0))),
            YamlDecoder.STRING_TO_STRING.fieldOf("args", Map.of()),
            YamlDecoder.STRING.fieldOf("world", "world"),
            AirdropSpawner.REGISTRY.decoder.fieldOf("spawner"),
            YamlDecoder.INT.fieldOf("nearby_radius", 30)
    ).preDecode(yaml -> {
        if (yaml.isMap()) {
            var map = yaml.asYamlMap().getOrThrow().deepCopy();
            if (map.has("defaults")) {
                var defaults = YamlDecoder.STRING_TO_STRING.decode(map.get("defaults")).getOrThrow();
                var placeholders = Placeholders.<Void>create();
                defaults.forEach(placeholders::of);
                var tmp = map.getRaw().remove("defaults");
                var replacer = placeholders.bind(null);
                map.deepReplace(replacer::setPlaceholders);
                map.getRaw().put("defaults", tmp);
                return map.get();
            }
        }
        return yaml;
    });
    private final String id;
    private final Map<String, ComponentFactory> nodes;
    public final Generator generator;
    private final Map<String, String> args;
    private final String world;
    private final AirdropSpawner spawner;
    private final int nearbyRadius;


    public Workflow(String id, Map<String, ComponentFactory> nodes, Generator generator, Map<String, String> args, String world, AirdropSpawner spawner, int nearbyRadius) {
        this.id = id;
        this.nodes = nodes;
        this.generator = generator;
        this.args = args;
        this.world = world;
        this.spawner = Objects.requireNonNull(spawner);
        this.nearbyRadius = nearbyRadius;
    }

    public AirdropSpawner spawner() {
        return spawner;
    }

    public TickableEntity createSpawner() {
        return spawner.create(this);
    }

    public Airdrop make(Location location) {
        Airdrop airdrop = new Airdrop(this, BAF.manager(), location);
        airdrop.args().putAll(args);
        return airdrop;
    }

    public Map<String, ComponentFactory> nodes() {
        return nodes;
    }

    public String id() {
        return id;
    }

    public Generator generator() {
        return generator;
    }

    public Map<String, String> args() {
        return args;
    }

    public String world() {
        return world;
    }

    public int nearbyRadius() {
        return nearbyRadius;
    }
}
