package dev.by1337.baf.config.value;

import dev.by1337.plc.PlaceholderApplier;
import dev.by1337.yaml.decoder.YamlDecoder;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Immutable configured expression. Only literals are parsed and cached at load time.
 */
public class Parameter<T> {
    private final String source;
    private final Function<String, T> parser;
    private final boolean constant;
    private final T cached;

    public Parameter(String source, Function<String, T> parser) {
        this.source = Objects.requireNonNull(source);
        this.parser = Objects.requireNonNull(parser);
        constant = !hasPlaceholders(source);
        cached = constant ? parse(source) : null;
    }

    private Parameter(T value) {
        source = String.valueOf(value);
        parser = ignored -> value;
        constant = true;
        cached = Objects.requireNonNull(value);
    }

    public static boolean hasPlaceholders(String source) {
        // BPlaceholder supports bracket expressions and PlaceholderAPI's %...% syntax.
        int bracket = source.indexOf('{');
        if (bracket >= 0 && source.indexOf('}', bracket + 1) > bracket + 1) return true;
        int percent = source.indexOf('%');
        return percent >= 0 && source.indexOf('%', percent + 1) > percent + 1;
    }

    public static <T> Parameter<T> constant(T value) {
        return new Parameter<>(value);
    }

    public static <T> YamlDecoder<Parameter<T>> decoder(Function<String, T> parser) {
        return YamlDecoder.STRING.map(source -> new Parameter<>(source, parser));
    }

    public static <E extends Enum<E>> YamlDecoder<Parameter<E>> enumDecoder(Class<E> type) {
        var codec = dev.by1337.yaml.codec.YamlCodec.fromEnum(type);
        return decoder(source -> codec.decode(dev.by1337.yaml.YamlValue.wrap(source)).getOrThrow());
    }

    public Parameter<T> checked(Predicate<T> condition, String message) {
        return new Parameter<>(source, value -> {
            T parsed = parser.apply(value);
            if (!condition.test(parsed)) throw new IllegalArgumentException(message);
            return parsed;
        });
    }

    public final T get(PlaceholderApplier placeholders) {
        return constant ? cached : parse(Objects.requireNonNull(placeholders).setPlaceholders(source));
    }

    protected final T parse(String value) {
        try {
            return Objects.requireNonNull(parser.apply(value));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Parameter '" + source + "' resolved to '" + value + "': " + error.getMessage(), error);
        }
    }

    public final String source() {
        return source;
    }

    public final boolean isConstant() {
        return constant;
    }

    @Override
    public String toString() {
        return source;
    }
}
