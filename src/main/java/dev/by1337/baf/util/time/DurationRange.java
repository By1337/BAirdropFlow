package dev.by1337.baf.util.time;

import dev.by1337.yaml.YamlValue;
import dev.by1337.yaml.decoder.YamlDecoder;

public class DurationRange {
    public static final YamlDecoder<DurationRange> DECODER = YamlDecoder.STRING.flatMap((c, s) -> {
        if (s.contains("-")) {
            var arr = s.split("-", 2);
            return DurationParser.MILLIS.decode(c, YamlValue.wrap(arr[0]))
                    .and(DurationParser.MILLIS.decode(c, YamlValue.wrap(arr[1])))
                    .map(DurationRange::new);
        } else {
            return DurationParser.MILLIS.decode(c, YamlValue.wrap(s)).map(f -> new DurationRange(f, -1));
        }
    });
    private final long from;
    private final long to;

    public DurationRange(long from, long to) {
        this.from = from;
        this.to = to;
    }

    public boolean contains(long ms) {
        if (to == -1) return ms == from;
        return from >= ms && to <= ms;
    }
}
