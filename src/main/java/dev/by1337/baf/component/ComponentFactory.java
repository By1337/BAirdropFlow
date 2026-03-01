package dev.by1337.baf.component;

import dev.by1337.baf.Airdrop;
import dev.by1337.yaml.YamlValue;
import dev.by1337.yaml.codec.DataResult;
import dev.by1337.yaml.codec.YamlCodec;
import dev.by1337.yaml.codec.schema.SchemaType;
import dev.by1337.yaml.codec.schema.SchemaTypes;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface ComponentFactory {
    AirdropComponent create(Airdrop airdrop, String name);

    YamlCodec<? extends ComponentFactory> codec();

    YamlCodec<ComponentFactory> CODEC = new YamlCodec<>() {
        @Override
        public DataResult<ComponentFactory> decode(YamlValue yaml) {
            return YAML_MAP.decode(yaml).flatMap(map -> {
                String type = map.get("type").asString(null);
                if (type == null) return DataResult.error("has no type!");
                var codec = AirdropComponent.REGISTRY.get(type);
                if (codec == null) return DataResult.error("Unknown node type {}", type);
                return codec.decode(yaml).mapValue(v -> v);
            });
        }

        @Override
        public YamlValue encode(ComponentFactory component) {
            YamlCodec<? extends ComponentFactory> codec = component.codec();
            NamespacedKey key = AirdropComponent.REGISTRY.getKey(codec);
            // noinspection all
            YamlValue val = ((YamlCodec) codec).encode(component);
            if (val.getRaw() instanceof Map) {
                // noinspection unchecked
                Map<String, Object> map = (Map<String, Object>) val.getRaw();
                map.put("type",
                        key == null ? codec.getClass().getSimpleName() : key.asString()
                );
                return YamlValue.wrap(map);
            }
            return val;
        }

        @Override
        public @NotNull SchemaType schema() {
            return SchemaTypes.ANY;
        }
    };
}
