package dev.by1337.baf.config.value;

import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.yaml.decoder.YamlDecoder;

public final class BooleanValue extends Parameter<Boolean> {
    public static final YamlDecoder<BooleanValue> DECODER = YamlDecoder.STRING.map(BooleanValue::new);

    public BooleanValue(String source) {
        super(source, value -> bool(value));
    }

    public BooleanValue(boolean value) {
        this(String.valueOf(value));
    }

    public boolean getBoolean(PlaceholderApplier placeholders) {
        return get(placeholders);
    }

    private static boolean bool(String value) {
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Expected true or false");
        };
    }
}
