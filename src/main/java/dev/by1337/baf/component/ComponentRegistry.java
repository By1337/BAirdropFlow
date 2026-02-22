package dev.by1337.baf.component;

import dev.by1337.yaml.codec.YamlCodec;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ComponentRegistry {

    private final Map<NamespacedKey, YamlCodec<? extends ComponentFactory>> key2value = new HashMap<>();
    private final Map<String, List<YamlCodec<? extends ComponentFactory>>> path2Value = new HashMap<>();
    private final Map<YamlCodec<? extends ComponentFactory>, NamespacedKey> value2key = new IdentityHashMap<>();

    public void register(String key, YamlCodec<? extends ComponentFactory> value) {
        register(NamespacedKey.fromString(key), value);
    }

    public void register(NamespacedKey key, YamlCodec<? extends ComponentFactory> value) {
        key2value.put(key, value);
        path2Value.computeIfAbsent(key.getKey(), k -> new ArrayList<>()).add(value);
        value2key.put(value, key);
    }

    public @Nullable YamlCodec<? extends ComponentFactory> get(String s) {
        if (!s.contains(":")) {
            s = "default:" + s;
        }
        var key = NamespacedKey.fromString(s);
        return key2value.get(key);
    }

    public int size() {
        return key2value.size();
    }

    public @Nullable YamlCodec<? extends ComponentFactory> get(@NotNull NamespacedKey namespacedKey) {
        return key2value.get(namespacedKey);
    }
    public @Nullable NamespacedKey getKey(YamlCodec<? extends ComponentFactory> codec){
        return value2key.get(codec);
    }
}
