package dev.by1337.baf.registry;

import com.google.gson.JsonObject;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.NamespacedKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class SchemaRegistry<T> extends DecoderRegistry<T> {
    private final Map<String, JsonObject> schemas = new LinkedHashMap<>();

    public void register(String type, YamlDecoder<? extends T> decoder, NodeSchema schema) {
        NamespacedKey key = NamespacedKey.fromString(type);
        if (key == null) throw new IllegalArgumentException("Invalid type " + type);
        JsonObject json = Objects.requireNonNull(schema).jsonWithPlaceholders(key.toString());
        super.register(key, Objects.requireNonNull(decoder));
        schemas.put(key.toString(), json);
    }

    @Override
    public void register(NamespacedKey type, YamlDecoder<? extends T> decoder) {
        super.register(type, decoder);
        schemas.remove(type.toString());
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
