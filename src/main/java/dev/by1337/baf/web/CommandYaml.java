package dev.by1337.baf.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.by1337.yaml.YamlMap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class CommandYaml {
    private static final Set<String> LINKS = Set.of("create", "remove", "remove?", "rm", "rm?", "call", "call?", "destroy");
    private static final Pattern STRING_LINK = Pattern.compile("^\\[(?:create|remove\\??|rm\\??|call\\??|destroy)\\](?:\\s|$)");

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
        if (!(value instanceof List<?> list))
            throw new IllegalArgumentException("Нужен список команд: каждая начинается с «-»");
        for (Object item : list) rejectLinks(item);
        JsonElement result = JsonValues.GSON.toJsonTree(value);
        if (!result.isJsonArray()) throw new IllegalArgumentException("Нужен список команд");
        return result.getAsJsonArray();
    }

    private static void rejectLinks(Object value) {
        if (value instanceof String text && STRING_LINK.matcher(text.trim()).find()) {
            throw new IllegalArgumentException("create, remove, call и destroy задаются связями между нодами");
        }
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (LINKS.contains(String.valueOf(entry.getKey()))) {
                    throw new IllegalArgumentException("create, remove, call и destroy задаются связями между нодами");
                }
                rejectLinks(entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) rejectLinks(item);
        }
    }
}
