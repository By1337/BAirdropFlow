package dev.by1337.baf.spawner.global;

import dev.by1337.baf.AirdropManager;
import dev.by1337.baf.Workflow;
import dev.by1337.baf.spawner.AirdropSpawner;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
import dev.by1337.baf.workflow.WorkflowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalSpawnersTest {
    @TempDir Path directory;

    @Test
    void loadsNestedSchedulesSkipsInvalidFilesAndFindsNearestPublishedEvent() throws Exception {
        AirdropSpawnerBoot.boot();
        Workflow workflow = mock(Workflow.class);
        AirdropSpawner workflowSpawner = mock(AirdropSpawner.class);
        when(workflow.spawner()).thenReturn(workflowSpawner);
        when(workflowSpawner.isGpsPublish()).thenReturn(true);
        WorkflowRepository workflows = mock(WorkflowRepository.class);
        when(workflows.getById("example:default")).thenReturn(workflow);
        AirdropManager manager = mock(AirdropManager.class);
        Files.createDirectories(directory.resolve("nested"));
        String schedule = """
                type: default:by_time
                time_zone: Europe/Moscow
                publish_gps: true
                workflows:
                  "20:00": example:default
                """;
        Files.writeString(directory.resolve("nested/visible.yml"), schedule);
        Files.writeString(directory.resolve("hidden.yaml"), schedule.replace("true", "false"));
        Files.writeString(directory.resolve("invalid.yml"), schedule.replace("example:default", "missing"));
        Files.writeString(directory.resolve("ignored.txt"), "not yaml");
        List<String> errors = new ArrayList<>();
        GlobalSpawners spawners = new GlobalSpawners();
        assertEquals(-1, spawners.getSecondsUntilNextEvent());
        spawners.load(directory, workflows, manager, errors::add);
        assertEquals(2, spawners.all().size());
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("missing"));
        verify(manager, times(2)).addTicking(any(GlobalSpawner.class));
        long remaining = spawners.getSecondsUntilNextEvent();
        assertTrue(remaining >= 0 && remaining <= 86400);
        when(workflowSpawner.isGpsPublish()).thenReturn(false);
        assertEquals(-1, spawners.getSecondsUntilNextEvent());
    }
}
