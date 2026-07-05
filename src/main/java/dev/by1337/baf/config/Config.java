package dev.by1337.baf.config;

import dev.by1337.baf.command.AirdropCommands;
import dev.by1337.core.util.io.ResourceUtil;
import dev.by1337.edsl.EventContextFactory;
import dev.by1337.yaml.YamlMap;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Config {
    public static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(
            Config::new,
            EventContextFactory.decoder("messages.yml").fieldOf()
    );
    private static final Logger log = LoggerFactory.getLogger(Config.class);
    public final EventContextFactory eventCtx;

    public Config(EventContextFactory eventCtx) {
        this.eventCtx = eventCtx;
        eventCtx.setCommands(AirdropCommands.getCommands());
    }

    private static YamlDecoder<YamlMap> readFile(String name) {
        return YamlDecoder.fromContext(Plugin.class)
                .map(pl -> ResourceUtil.saveIfNotExist(name, pl))
                .and(YamlDecoder.READ_YAML_FROM_FILE);
    }
}
