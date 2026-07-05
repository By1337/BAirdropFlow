package dev.by1337.baf.registry;

import dev.by1337.yaml.codec.DataResult;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DecoderRegistry<T> {

    private final Map<NamespacedKey, YamlDecoder<? extends T>> key2value = new HashMap<>();
    private final Map<String, List<YamlDecoder<? extends T>>> path2Value = new HashMap<>();
    private final Map<YamlDecoder<? extends T>, NamespacedKey> value2key = new IdentityHashMap<>();

    public final YamlDecoder<T> decoder = (ctx, yaml) -> YamlDecoder.YAML_MAP.decode(ctx, yaml).flatMap(map -> {
        String type = map.get("type").asString(null);
        if (type == null) return DataResult.error("has no type!");
        var codec = get(type);
        if (codec == null) return DataResult.error("Unknown node type {}", type);
        return codec.decode(yaml).mapValue(v -> v);
    });

    public void register(String key, YamlDecoder<? extends T> value) {
        register(NamespacedKey.fromString(key), value);
    }

    public void register(NamespacedKey key, YamlDecoder<? extends T> value) {
        key2value.put(key, value);
        path2Value.computeIfAbsent(key.getKey(), k -> new ArrayList<>()).add(value);
        value2key.put(value, key);
    }

    public @Nullable YamlDecoder<? extends T> get(String s) {
        if (!s.contains(":")) {
            s = "default:" + s;
        }
        var key = NamespacedKey.fromString(s);
        return key2value.get(key);
    }

    public int size() {
        return key2value.size();
    }

    public @Nullable YamlDecoder<? extends T> get(@NotNull NamespacedKey namespacedKey) {
        return key2value.get(namespacedKey);
    }

    public @Nullable NamespacedKey getKey(YamlDecoder<? extends T> codec) {
        return value2key.get(codec);
    }

    public void clear() {
        key2value.clear();
        path2Value.clear();
        value2key.clear();
    }
}
