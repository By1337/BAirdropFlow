package dev.by1337.baf.config.value;

import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.yaml.decoder.YamlDecoder;

public final class StringValue extends Parameter<String> {
    public static final YamlDecoder<StringValue> DECODER = YamlDecoder.STRING.map(StringValue::new);

    public StringValue(String source) {
        super(source, value -> value);
    }

    public String getString(PlaceholderApplier placeholders) {
        return get(placeholders);
    }
}
