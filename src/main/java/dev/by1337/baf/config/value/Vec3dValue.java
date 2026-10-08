package dev.by1337.baf.config.value;

import dev.by1337.baf.util.Vec3d;
import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.yaml.decoder.YamlDecoder;

public final class Vec3dValue extends Parameter<Vec3d> {
    public static final YamlDecoder<Vec3dValue> DECODER = YamlDecoder.STRING.map(Vec3dValue::new);

    public Vec3dValue(String source) {
        super(source, Vec3dValue::parseVector);
    }

    public Vec3dValue(Vec3d value) {
        this(value.x() + ";" + value.y() + ";" + value.z());
    }

    public Vec3d getVector(PlaceholderApplier placeholders) {
        return get(placeholders);
    }

    private static Vec3d parseVector(String value) {
        String[] parts = value.split(";", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Expected X;Y;Z");
        double x = Double.parseDouble(parts[0].trim());
        double y = Double.parseDouble(parts[1].trim());
        double z = Double.parseDouble(parts[2].trim());
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Vector coordinates must be finite");
        return new Vec3d(x, y, z);
    }
}
