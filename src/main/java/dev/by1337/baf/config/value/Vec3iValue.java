package dev.by1337.baf.config.value;

import dev.by1337.baf.util.Vec3i;
import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.yaml.decoder.YamlDecoder;

public final class Vec3iValue extends Parameter<Vec3i> {
    public static final YamlDecoder<Vec3iValue> DECODER = YamlDecoder.STRING.map(Vec3iValue::new);

    public Vec3iValue(String source) {
        super(source, Vec3iValue::parseVector);
    }

    public Vec3iValue(Vec3i value) {
        this(value.x() + ";" + value.y() + ";" + value.z());
    }

    public Vec3i getVector(PlaceholderApplier placeholders) {
        return get(placeholders);
    }

    private static Vec3i parseVector(String value) {
        String[] parts = value.split(";", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Expected X;Y;Z");
        int x = Integer.parseInt(parts[0].trim());
        int y = Integer.parseInt(parts[1].trim());
        int z = Integer.parseInt(parts[2].trim());

        return new Vec3i(x, y, z);
    }
}
