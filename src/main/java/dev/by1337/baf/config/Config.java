package dev.by1337.baf.config;

import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

public class Config {
    public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
            Config::new,
            YamlDecoder.BOOL.fieldOf("debug", true)
    );
    public final boolean debug;

    public Config(boolean debug) {
        this.debug = debug;
    }
}
