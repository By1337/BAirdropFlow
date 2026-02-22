package dev.by1337.baf;

import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;

import java.util.Map;

public class AirdropConfig {
    public static final YamlCodec<AirdropConfig> CODEC = RecordYamlCodecBuilder.mapOf(
            AirdropConfig::new,
            YamlCodec.mapOf(YamlCodec.STRING, ComponentFactory.CODEC)
                    .fieldOf("nodes", v -> v.nodes)
    );
    private final Map<String, ComponentFactory> nodes;

    public AirdropConfig(Map<String, ComponentFactory> nodes) {
        this.nodes = nodes;
    }

    public Map<String, ComponentFactory> nodes() {
        return nodes;
    }
}
