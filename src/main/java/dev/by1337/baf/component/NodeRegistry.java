package dev.by1337.baf.component;

import dev.by1337.baf.registry.SchemaRegistry;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.NamespacedKey;

public final class NodeRegistry extends SchemaRegistry<ComponentFactory> {

    @Override
    public void register(String type, YamlDecoder<? extends ComponentFactory> decoder) {
        throw new IllegalArgumentException("Register component decoder together with its NodeSchema");
    }

    @Override
    public void register(NamespacedKey type, YamlDecoder<? extends ComponentFactory> decoder) {
        throw new IllegalArgumentException("Register component decoder together with its NodeSchema");
    }

}
