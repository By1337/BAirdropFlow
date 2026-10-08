package dev.by1337.baf.config.value;

import dev.by1337.yaml.decoder.YamlDecoder;

import java.util.OptionalDouble;

public final class TimeValue extends Parameter<OptionalDouble> {
    public static final YamlDecoder<TimeValue> DECODER = YamlDecoder.STRING.map(TimeValue::new);

    public TimeValue(String source) {
        super(source, value -> {
            if (value.isBlank()) return OptionalDouble.empty();
            double seconds = Double.parseDouble(value.trim());
            if (!Double.isFinite(seconds)) throw new IllegalArgumentException("Expected finite seconds");
            return OptionalDouble.of(seconds);
        });
    }
}
