package dev.by1337.baf.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.by1337.yaml.YamlMap;

import java.util.List;

public final class CommandYaml {
    private CommandYaml() {
    }

    public static JsonArray parse(String yaml) {
        if (yaml == null || yaml.isBlank()) return new JsonArray();
        if (yaml.length() > 256_000) throw new IllegalArgumentException("Текст команд слишком большой");
        Object value;
        try {
            value = YamlMap.loadFromString("commands:\n" + yaml.indent(2)).getRaw("commands");
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Ошибка YAML: " + e.getMessage(), e);
        }
        if (!(value instanceof List<?>))
            throw new IllegalArgumentException("Нужен список команд: каждая начинается с «-»");
        JsonElement result = JsonValues.GSON.toJsonTree(value);
        if (!result.isJsonArray()) throw new IllegalArgumentException("Нужен список команд");
        return result.getAsJsonArray();
    }
}
