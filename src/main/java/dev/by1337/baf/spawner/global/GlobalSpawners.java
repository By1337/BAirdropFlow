package dev.by1337.baf.spawner.global;

import dev.by1337.baf.AirdropManager;
import dev.by1337.baf.workflow.WorkflowRepository;
import dev.by1337.yaml.YamlMap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GlobalSpawners {
    private final List<GlobalSpawner> spawners = new ArrayList<>();

    public void load(Path directory, WorkflowRepository workflows, AirdropManager manager,
                     Consumer<String> logger) throws IOException {
        Files.createDirectories(directory);
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .sorted().toList()) {
                try {
                    GlobalSpawner spawner = GlobalSpawner.REGISTRY.decoder
                            .decode(YamlMap.load(file.toFile()).get()).getOrThrow();
                    spawner.start(workflows::getById, manager::addTicking);
                    spawners.add(spawner);
                    manager.addTicking(spawner);
                } catch (Exception e) {
                    logger.accept(file + ": " + e.getMessage());
                }
            }
        }
    }

    public List<GlobalSpawner> all() {
        return List.copyOf(spawners);
    }

    public long getSecondsUntilNextEvent() {
        return spawners.stream().filter(s -> !s.isRemoved() && s.isGpsPublish())
                .mapToLong(GlobalSpawner::getSecondsUntilNextEvent).filter(seconds -> seconds >= 0)
                .min().orElse(-1);
    }
}
