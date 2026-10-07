package dev.by1337.baf.spawner.global;

import dev.by1337.baf.Workflow;
import dev.by1337.baf.entity.TickableEntity;
import dev.by1337.baf.spawner.AirdropSpawner;
import dev.by1337.yaml.YamlMap;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ByTimeSpawnerTest {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-05T16:59:59.500Z"); // Monday 19:59:59.5 MSK
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }

    private Workflow workflow(boolean gps) {
        Workflow workflow = mock(Workflow.class);
        AirdropSpawner spawner = mock(AirdropSpawner.class);
        when(workflow.spawner()).thenReturn(spawner);
        when(spawner.isGpsPublish()).thenReturn(gps);
        when(workflow.createSpawner()).thenReturn(mock(TickableEntity.class));
        return workflow;
    }

    @Test
    void launchesWorkflowSpawnerOnceAndAdvancesDailyWithoutReplayingMissedDays() {
        TestClock clock = new TestClock();
        Workflow workflow = workflow(true);
        List<TickableEntity> launched = new ArrayList<>();
        var spawner = new ByTimeSpawner(MOSCOW, true,
                Map.of(ByTimeSpawner.Schedule.parse("20:00"), Map.of("example:default", 1D)), clock);
        spawner.start(id -> workflow, launched::add);
        assertEquals(1, spawner.getSecondsUntilNextEvent());
        spawner.tick();
        assertTrue(launched.isEmpty());
        clock.now = Instant.parse("2026-10-05T17:00:00Z");
        spawner.tick();
        spawner.tick();
        assertEquals(List.of(workflow.createSpawner()), launched);
        assertEquals(86400, spawner.getSecondsUntilNextEvent());
        clock.now = clock.now.plus(Duration.ofDays(3)).plusSeconds(10);
        spawner.tick();
        assertEquals(2, launched.size());
        assertEquals(86390, spawner.getSecondsUntilNextEvent());
    }

    @Test
    void weeklySchedulesRespectDayAndTimezone() {
        Instant now = Instant.parse("2026-10-05T17:00:00Z");
        var monday = ByTimeSpawner.Schedule.parse("monday 20:00");
        assertEquals(now, monday.next(now, MOSCOW, true));
        assertEquals(now.plus(Duration.ofDays(7)), monday.next(now, MOSCOW, false));
        assertEquals(Instant.parse("2026-10-06T15:00:00Z"),
                ByTimeSpawner.Schedule.parse("TUESDAY 18:00").next(now, MOSCOW, true));
        assertEquals(Instant.parse("2026-10-05T20:00:00Z"), monday.next(now, ZoneOffset.UTC, true));
    }

    @Test
    void hiddenSchedulesDoNotPublishButStillLaunchAndNearestVisibleWorkflowWins() {
        TestClock clock = new TestClock();
        Workflow hidden = workflow(false);
        Workflow visible = workflow(true);
        Map<String, Workflow> workflows = Map.of("hidden", hidden, "visible", visible);
        List<TickableEntity> launched = new ArrayList<>();
        var schedules = Map.of(ByTimeSpawner.Schedule.parse("20:00"), Map.of("hidden", 1D),
                ByTimeSpawner.Schedule.parse("21:00"), Map.of("visible", 1D));
        var spawner = new ByTimeSpawner(MOSCOW, true, schedules, clock);
        assertEquals(-1, spawner.getSecondsUntilNextEvent());
        spawner.start(workflows::get, launched::add);
        assertEquals(3601, spawner.getSecondsUntilNextEvent());
        clock.now = Instant.parse("2026-10-05T17:00:00Z");
        spawner.tick();
        assertEquals(List.of(hidden.createSpawner()), launched);
        var unpublished = new ByTimeSpawner(MOSCOW, false, schedules, clock);
        unpublished.start(workflows::get, launched::add);
        assertEquals(-1, unpublished.getSecondsUntilNextEvent());
        var onlyHidden = new ByTimeSpawner(MOSCOW, true,
                Map.of(ByTimeSpawner.Schedule.parse("20:00"), Map.of("hidden", 1D)), clock);
        onlyHidden.start(workflows::get, launched::add);
        assertEquals(-1, onlyHidden.getSecondsUntilNextEvent());
    }

    @Test
    void decoderAcceptsSingleIdsWeightedChoicesAndWeeklyKeysAndRejectsInvalidConfig() {
        var decoded = ByTimeSpawner.DECODER.decode(YamlMap.loadFromString("""
                time_zone: Europe/Moscow
                publish_gps: true
                workflows:
                  "20:00": example:default
                  "FRIDAY 21:00":
                    example:default: 10
                    example:default2: 20
                """).get());
        assertFalse(decoded.hasError(), decoded.error());
        assertTrue(decoded.getOrThrow().isGpsPublish());
        for (String yaml : List.of("time_zone: Unknown/Zone\nworkflows: {}",
                "workflows: { '25:00': example:default }",
                "workflows: { 'FUNDAY 20:00': example:default }",
                "workflows: { '20:00': { example:default: 0 } }",
                "workflows: { '20:00': {} }")) {
            assertTrue(ByTimeSpawner.DECODER.decode(YamlMap.loadFromString(yaml).get()).hasError(), yaml);
        }
    }

    @Test
    void weightedChoiceAdvertisesOnlyTheWorkflowThatWillActuallyLaunch() {
        TestClock clock = new TestClock();
        Workflow hidden = workflow(false);
        Workflow visible = workflow(true);
        Map<String, Workflow> workflows = Map.of("hidden", hidden, "visible", visible);
        List<TickableEntity> launched = new ArrayList<>();
        var spawner = new ByTimeSpawner(MOSCOW, true,
                Map.of(ByTimeSpawner.Schedule.parse("20:00"), Map.of("hidden", 10D, "visible", 20D)), clock);
        spawner.start(workflows::get, launched::add);
        long seconds = spawner.getSecondsUntilNextEvent();
        assertEquals(seconds, spawner.getSecondsUntilNextEvent());
        clock.now = Instant.parse("2026-10-05T17:00:00Z");
        spawner.tick();
        assertEquals(List.of(seconds < 0 ? hidden.createSpawner() : visible.createSpawner()), launched);
    }

    @Test
    void emptyOrUnknownWorkflowsCannotAdvertiseAnEvent() {
        var empty = new ByTimeSpawner(MOSCOW, true, Map.of(), new TestClock());
        empty.start(id -> null, entity -> fail());
        assertEquals(-1, empty.getSecondsUntilNextEvent());
        var unknown = new ByTimeSpawner(MOSCOW, true,
                Map.of(ByTimeSpawner.Schedule.parse("20:00"), Map.of("missing", 1D)), new TestClock());
        assertThrows(IllegalArgumentException.class, () -> unknown.start(id -> null, entity -> fail()));
        assertEquals(-1, unknown.getSecondsUntilNextEvent());
    }
}
