package dev.by1337.baf.config.value;

import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.yaml.YamlValue;
import dev.by1337.yaml.decoder.BukkitYamlDecoders;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Material;

public final class MaterialValue extends Parameter<Material> {
    public static final YamlDecoder<MaterialValue> DECODER = YamlDecoder.STRING.map(MaterialValue::new);

    public MaterialValue(String source) {
        super(source, value -> BukkitYamlDecoders.material().decode(YamlValue.wrap(value)).getOrThrow());
    }

    public MaterialValue(Material material) {
        this(material.getKey().asString());
    }

    public Material getMaterial(PlaceholderApplier placeholders) {
        return get(placeholders);
    }
}
