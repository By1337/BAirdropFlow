package dev.by1337.baf.particle;

import dev.by1337.particle.flow.emit.EmitterGraph;
import dev.by1337.yaml.YamlMap;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ParticleGraphs {
    private final Plugin plugin;
    private final File root;
    private Map<String, EmitterGraph> graphs = Map.of();

    public ParticleGraphs(Plugin plugin) {
        this.plugin = plugin;
        this.root = new File(plugin.getDataFolder(), "particles");
    }

    public void load() {
        File[] files = root.listFiles(file -> file.isFile()
                && (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")));
        if (files == null) {
            plugin.getSLF4JLogger().warn("Cannot read particle directory: {}", root);
            graphs = Map.of();
            return;
        }
        Map<String, EmitterGraph> loaded = new HashMap<>();
        for (File file : files) {
            String filename = file.getName();
            String key = filename.substring(0, filename.lastIndexOf('.'));
            try {
                EmitterGraph graph = EmitterGraph.DECODER.decode(YamlMap.load(file).get()).getOrThrow();
                if (loaded.putIfAbsent(key, graph) != null) {
                    plugin.getSLF4JLogger().warn("Duplicate particle graph name: {}", key);
                }
            } catch (Exception e) {
                plugin.getSLF4JLogger().warn("Failed to load particle graph {}", file, e);
            }
        }
        graphs = Map.copyOf(loaded);
        plugin.getSLF4JLogger().info("Loaded {} particle graph(s)", graphs.size());
    }

    public EmitterGraph get(String id) {
        return graphs.get(id);
    }
}
