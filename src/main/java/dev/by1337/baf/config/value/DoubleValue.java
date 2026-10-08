package dev.by1337.baf.config.value;

import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.yaml.decoder.YamlDecoder;

public final class DoubleValue extends Parameter<Double> {
    public static final YamlDecoder<DoubleValue> DECODER = YamlDecoder.STRING.map(s -> new DoubleValue(s));

    public DoubleValue(String source) {
        super(source, value -> finite(value));
    }

    public DoubleValue(double value) {
        this(String.valueOf(value));
    }

    public double getDouble(PlaceholderApplier placeholders) {
        return get(placeholders);
    }

    private static double finite(String value) {
        double parsed = Double.parseDouble(value.trim());
        if (!Double.isFinite(parsed)) throw new IllegalArgumentException("Expected a finite number");
        return parsed;
    }

    private DoubleValue(String source, java.util.function.Function<String, Double> parser) {
        super(source, parser);
    }

    @Override
    public DoubleValue checked(java.util.function.Predicate<Double> condition, String message) {
        return new DoubleValue(source(), value -> {
            Double parsed = parse(value);
            if (!condition.test(parsed)) throw new IllegalArgumentException(message);
            return parsed;
        });
    }
}
