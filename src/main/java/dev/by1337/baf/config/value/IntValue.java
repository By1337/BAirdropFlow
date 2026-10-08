package dev.by1337.baf.config.value;

import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.yaml.decoder.YamlDecoder;

public final class IntValue extends Parameter<Integer> {
    public static final YamlDecoder<IntValue> DECODER = YamlDecoder.STRING.map(s -> new IntValue(s));

    public IntValue(String source) {
        super(source, value -> Integer.parseInt(value.trim()));
    }

    public IntValue(int value) {
        this(String.valueOf(value));
    }

    public int getInt(PlaceholderApplier placeholders) {
        return get(placeholders);
    }

    private IntValue(String source, java.util.function.Function<String, Integer> parser) {
        super(source, parser);
    }

    @Override
    public IntValue checked(java.util.function.Predicate<Integer> condition, String message) {
        return new IntValue(source(), value -> {
            Integer parsed = parse(value);
            if (!condition.test(parsed)) throw new IllegalArgumentException(message);
            return parsed;
        });
    }
}
