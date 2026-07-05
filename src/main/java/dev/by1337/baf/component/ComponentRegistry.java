package dev.by1337.baf.component;

import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ComponentRegistry {

    private final Map<NamespacedKey, YamlDecoder<? extends ComponentFactory>> key2value = new HashMap<>();
    private final Map<String, List<YamlDecoder<? extends ComponentFactory>>> path2Value = new HashMap<>();
    private final Map<YamlDecoder<? extends ComponentFactory>, NamespacedKey> value2key = new IdentityHashMap<>();

    public void register(String key, YamlDecoder<? extends ComponentFactory> value) {
        register(NamespacedKey.fromString(key), value);
    }

    public void register(NamespacedKey key, YamlDecoder<? extends ComponentFactory> value) {
        key2value.put(key, value);
        path2Value.computeIfAbsent(key.getKey(), k -> new ArrayList<>()).add(value);
        value2key.put(value, key);
    }

    public @Nullable YamlDecoder<? extends ComponentFactory> get(String s) {
        if (!s.contains(":")) {
            s = "default:" + s;
        }
        var key = NamespacedKey.fromString(s);
        return key2value.get(key);
    }

    public int size() {
        return key2value.size();
    }

    public @Nullable YamlDecoder<? extends ComponentFactory> get(@NotNull NamespacedKey namespacedKey) {
        return key2value.get(namespacedKey);
    }

    public @Nullable NamespacedKey getKey(YamlDecoder<? extends ComponentFactory> codec) {
        return value2key.get(codec);
    }
}
