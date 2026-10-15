package dev.by1337.baf.config;

import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

public class Config {
    public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
            Config::new,
            YamlDecoder.BOOL.fieldOf("debug", true),
            WebConfig.DECODER.fieldOf("web", new WebConfig("wss://btunnel.bdev.space/api/ws", "", "BAirdropFlow"))
    );
    public final boolean debug;
    public final WebConfig web;

    public Config(boolean debug, WebConfig web) {
        this.debug = debug;
        this.web = web;
    }
}
