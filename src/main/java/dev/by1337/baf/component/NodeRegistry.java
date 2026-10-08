package dev.by1337.baf.component;

import com.google.gson.JsonObject;
import dev.by1337.baf.registry.DecoderRegistry;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.NamespacedKey;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NodeRegistry extends DecoderRegistry<ComponentFactory> {
    private final Map<String, JsonObject> schemas = new LinkedHashMap<>();

    public void register(String type, YamlDecoder<? extends ComponentFactory> decoder, NodeSchema schema) {
        NamespacedKey key = NamespacedKey.fromString(type);
        if (key == null) throw new IllegalArgumentException("Invalid component type " + type);
        super.register(key, decoder);
        schemas.put(type, schema.jsonWithPlaceholders(type));
    }

    @Override
    public void register(String type, YamlDecoder<? extends ComponentFactory> decoder) {
        throw new IllegalArgumentException("Register component decoder together with its NodeSchema");
    }

    @Override
    public void register(NamespacedKey type, YamlDecoder<? extends ComponentFactory> decoder) {
        throw new IllegalArgumentException("Register component decoder together with its NodeSchema");
    }

    public Map<String, JsonObject> schemas() {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        schemas.forEach((key, schema) -> result.put(key, schema.deepCopy()));
        return result;
    }

    @Override
    public void clear() {
        super.clear();
        schemas.clear();
    }
}
