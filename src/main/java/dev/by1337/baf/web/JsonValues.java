package dev.by1337.baf.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.by1337.web.client.StreamJsonWriter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonValues {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private JsonValues() {
    }

    public static Object raw(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonObject()) {
            Map<String, Object> result = new LinkedHashMap<>();
            value.getAsJsonObject().entrySet().forEach(e -> result.put(e.getKey(), raw(e.getValue())));
            return result;
        }
        if (value.isJsonArray()) {
            List<Object> result = new ArrayList<>();
            value.getAsJsonArray().forEach(v -> result.add(raw(v)));
            return result;
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isString()) {
            String text = primitive.getAsString();
            if (text.codePoints().anyMatch(c -> c < 0x20 && c != '\t' && c != '\n' && c != '\r' ||
                    c >= 0x7F && c < 0xA0 && c != 0x85 || c >= 0xD800 && c <= 0xDFFF || c == 0xFFFE || c == 0xFFFF)) {
                throw new IllegalArgumentException("Строка содержит управляющие символы, недопустимые в YAML");
            }
            return text;
        }
        BigDecimal number = primitive.getAsBigDecimal();
        try {
            return number.intValueExact();
        } catch (ArithmeticException ignored) {
        }
        try {
            return number.longValueExact();
        } catch (ArithmeticException ignored) {
        }
        double result = number.doubleValue();
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Число выходит за допустимый диапазон");
        return result;
    }

    public static void write(StreamJsonWriter writer, String key, JsonElement value) {
        if (value == null || value.isJsonNull()) {
            writer.putNull(key);
            return;
        }
        if (value.isJsonObject()) {
            try (var ignored = writer.startObject(key)) {
                value.getAsJsonObject().entrySet().forEach(e -> write(writer, e.getKey(), e.getValue()));
            }
        } else if (value.isJsonArray()) {
            try (var ignored = writer.startArray(key)) {
                value.getAsJsonArray().forEach(v -> write(writer, null, v));
            }
        } else {
            JsonPrimitive p = value.getAsJsonPrimitive();
            if (p.isBoolean()) writer.putBoolean(key, p.getAsBoolean());
            else if (p.isString()) writer.putString(key, p.getAsString());
            else {
                try {
                    writer.putLong(key, p.getAsBigDecimal().longValueExact());
                } catch (ArithmeticException ignored) {
                    writer.putDouble(key, p.getAsDouble());
                }
            }
        }
    }
}
