package dev.by1337.baf.config;

import dev.by1337.core.util.io.ResourceUtil;
import dev.by1337.yaml.YamlMap;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Installs each default folder only when that folder is absent. */
public final class DefaultResources {
    private record Index(Map<String, List<String>> folders, List<String> files) {
        private static final YamlDecoder<Index> DECODER = RecordYamlDecoder.mapOf(
                Index::new,
                YamlDecoder.mapOf(YamlDecoder.STRING, YamlDecoder.STRING.listOf()).fieldOf("folders"),
                YamlDecoder.STRING.listOf().fieldOf("files")
        );
    }

    private DefaultResources() {}

    public static void save(Plugin plugin) {
        Index index;
        try (var input = Objects.requireNonNull(plugin.getResource("default-resources.yml"),
                "Missing default-resources.yml")) {
            index = Index.DECODER.decode(YamlMap.load(input).get()).getOrThrow();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        index.folders().forEach((folder, files) -> {
            File destination = new File(plugin.getDataFolder(), folder);
            if (destination.exists()) return;
            if (!destination.mkdirs()) throw new IllegalStateException("Cannot create " + destination);
            for (String file : files) ResourceUtil.saveIfNotExist(folder + "/" + file, plugin);
        });
        for (String file : index.files()) ResourceUtil.saveIfNotExist(file, plugin);
    }
}
