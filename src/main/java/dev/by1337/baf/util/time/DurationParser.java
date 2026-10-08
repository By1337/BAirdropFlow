package dev.by1337.baf.util.time;

import dev.by1337.yaml.decoder.YamlDecoder;

public class DurationParser {
    public static final YamlDecoder<Long> MILLIS = YamlDecoder.STRING.map(DurationParser::parseMillis);

    public static long parseMillis(String s) {
        final StringBuilder number = new StringBuilder();
        final StringBuilder type = new StringBuilder();
        long out = 0;
        char[] arr = s.toCharArray();

        for (char c : arr) {
            if (Character.isDigit(c)) {
                if (!type.isEmpty() && !number.isEmpty()) {
                    out += getResult(Integer.parseInt(number.toString()), type.toString());
                    number.setLength(0);
                    type.setLength(0);
                }
                number.append(c);
            } else {
                type.append(c);
            }
        }
        if (!type.isEmpty() && !number.isEmpty()) {
            out += getResult(Integer.parseInt(number.toString()), type.toString());
        }
        return out;
    }

    private static long getResult(int x, String s) {
        return switch (s) {
            case "s" -> 1000L * x;
            case "m" -> 60000L * x;
            case "h" -> 3600000L * x;
            case "d" -> 86400000L * x;
            case "w" -> 604800000L * x;
            case "mo" -> 2629746000L * x;
            case "y" -> 31556908800L * x;
            default -> 0;
        };
    }
}
