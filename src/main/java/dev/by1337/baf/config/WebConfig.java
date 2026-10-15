package dev.by1337.baf.config;

import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

public record WebConfig(String url, String secret, String description) {
    public static final YamlDecoder<WebConfig> DECODER = RecordYamlDecoder.mapOf(
            WebConfig::new,
            YamlDecoder.STRING.fieldOf("url", "wss://btunnel.bdev.space/api/ws"),
            YamlDecoder.STRING.fieldOf("secret", ""),
            YamlDecoder.STRING.fieldOf("description", "BAirdropFlow")
    );
}
