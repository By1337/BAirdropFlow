package dev.by1337.baf.spawner.global;

import dev.by1337.baf.AirdropManager;
import dev.by1337.baf.Workflow;
import dev.by1337.baf.spawner.AirdropSpawner;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
import dev.by1337.baf.workflow.WorkflowRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalSpawnersTest {
    @TempDir
    Path directory;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void loadsNestedSchedulesSkipsInvalidFilesAndFindsNearestPublishedEvent() throws Exception {
        AirdropSpawnerBoot.boot();
        Workflow workflow = mock(Workflow.class);
        AirdropSpawner workflowSpawner = mock(AirdropSpawner.class);
        when(workflow.spawner()).thenReturn(workflowSpawner);
        when(workflowSpawner.isGpsPublish()).thenReturn(true);
        var launches = new dev.by1337.baf.launch.LaunchRegistry();
        launches.publish("workflow", "example:default", workflow);
        when(workflow.isGpsPublish()).thenAnswer(c -> workflowSpawner.isGpsPublish());
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
        spawners.load(directory, launches, manager, errors::add);
        assertEquals(2, spawners.all().size());
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().contains("missing"));
        verify(manager, times(2)).addTicking(any(GlobalSpawner.class));
        long remaining = spawners.getSecondsUntilNextEvent();
        assertTrue(remaining >= 0 && remaining <= 86400);
        when(workflowSpawner.isGpsPublish()).thenReturn(false);
        assertEquals(-1, spawners.getSecondsUntilNextEvent());
    }
    @Test
    void voteDefinitionsArePublishedBeforeSchedulesAndBadVotesAreNotPublished() throws Exception {
        AirdropSpawnerBoot.boot();
        var launches = new dev.by1337.baf.launch.LaunchRegistry();
        launches.publish("event", "event:a", () -> {});
        AirdropManager manager = mock(AirdropManager.class);
        Files.writeString(directory.resolve("a-time.yml"), "type: default:by_time\nevents: { '13:00': event:vote }\n");
        Files.writeString(directory.resolve("z-vote.yml"), "type: default:vote\nid: event:vote\noptions: { 'event:a': A }\n");
        Files.writeString(directory.resolve("bad-vote.yml"), "type: default:vote\nid: event:bad\noptions: { missing: Missing }\n");
        Files.writeString(directory.resolve("cycle.yml"), "type: default:vote\nid: event:cycle\noptions: { 'event:cycle': Self }\n");
        List<String> errors = new ArrayList<>();
        var spawners = new GlobalSpawners();
        spawners.load(directory, launches, manager, errors::add);
        assertEquals(2, spawners.all().size()); assertEquals(2, errors.size());
        assertNull(spawners.voting().active()); assertNull(launches.get("event:bad")); assertNull(launches.get("event:cycle"));
        launches.start("event:vote"); var session = spawners.voting().active();
        assertNotNull(session);
        spawners.close(); assertTrue(session.isRemoved()); assertNull(launches.get("event:vote"));
        assertNotNull(launches.get("event:a"));
    }

}
