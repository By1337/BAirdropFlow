package dev.by1337.baf.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class OptionsHolder {
    private final Map<String, String> options;

    public OptionsHolder() {
        options = new HashMap<>();
    }

    public OptionsHolder(@NotNull String input) {
        this();
        fromString(input);
    }

    public void fromString(@NotNull String input) {
        if (input.isBlank()) return;
        String[] arr = input.split(" ");
        String flag = null;
        for (String s : arr) {
            if (s.startsWith("-")) {
                flag = s.substring(1);
            } else {
                String val = options.get(Objects.requireNonNull(flag, "missing flag! Use -<flag> <value>"));
                options.put(flag, val == null ? s : val + " " + s);
            }
        }
    }

    public Map<String, String> getOptions() {
        return options;
    }

    @Nullable
    public String get(String flag) {
        return options.get(flag);
    }

    public void putIfNotExist(String flag, String value) {
        if (!options.containsKey(flag)) {
            options.put(flag, value);
        }
    }

    @NotNull
    public String getOrDefault(String flag, @NotNull String def) {
        return options.getOrDefault(flag, def);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        options.forEach((k, v) -> sb.append("-").append(k).append(" ").append('"').append(v).append('"').append(" "));
        return sb.toString();
    }
}