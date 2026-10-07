package dev.by1337.baf.spawner.global;

import dev.by1337.baf.Workflow;
import dev.by1337.baf.entity.TickableEntity;
import dev.by1337.bmenu.random.WeightedItem;
import dev.by1337.bmenu.random.WeightedItemSelector;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ByTimeSpawner implements GlobalSpawner {
    private static final Logger log = LoggerFactory.getLogger(ByTimeSpawner.class);
    private static final YamlDecoder<Map<String, Double>> CHOICES =
            YamlDecoder.mapOf(YamlDecoder.STRING, YamlDecoder.DOUBLE)
                    .whenPrimitive(YamlDecoder.STRING.map(id -> Map.of(id, 1D)))
                    .postDecode(choices -> {
                        if (choices.isEmpty() || choices.values().stream().anyMatch(w -> !Double.isFinite(w) || w <= 0))
                            throw new IllegalArgumentException("Workflow weights must be finite and positive");
                    });
    public static final YamlDecoder<ByTimeSpawner> DECODER = RecordYamlDecoder.mapOf(
            ByTimeSpawner::new,
            YamlDecoder.STRING.map(value -> ZoneId.of(value)).fieldOf("time_zone", ZoneId.systemDefault()),
            YamlDecoder.BOOL.fieldOf("publish_gps", false),
            YamlDecoder.mapOf(YamlDecoder.STRING.map(Schedule::parse), CHOICES).fieldOf("workflows")
    );

    private final ZoneId zone;
    private final boolean publishGps;
    private final Map<Schedule, Map<String, Double>> schedules;
    private final Map<Schedule, Instant> next = new LinkedHashMap<>();
    private final Map<Schedule, String> selected = new HashMap<>();
    private final Clock clock;
    private Function<String, Workflow> workflows;
    private Consumer<TickableEntity> launch;

    public ByTimeSpawner(ZoneId zone, boolean publishGps, Map<Schedule, Map<String, Double>> schedules) {
        this(zone, publishGps, schedules, Clock.systemUTC());
    }

    ByTimeSpawner(ZoneId zone, boolean publishGps, Map<Schedule, Map<String, Double>> schedules, Clock clock) {
        this.zone = zone;
        this.publishGps = publishGps;
        this.schedules = new LinkedHashMap<>(schedules);
        this.clock = clock;
    }

    @Override
    public void start(Function<String, Workflow> workflows, Consumer<TickableEntity> launch) {
        for (var choices : schedules.values()) {
            for (String id : choices.keySet()) {
                if (workflows.apply(id) == null) throw new IllegalArgumentException("Unknown workflow: " + id);
            }
        }
        this.workflows = workflows;
        this.launch = launch;
        next.clear();
        selected.clear();
        Instant now = clock.instant();
        schedules.keySet().forEach(schedule -> {
            next.put(schedule, schedule.next(now, zone, true));
            selected.put(schedule, select(schedule));
        });
    }

    @Override
    public boolean isGpsPublish() {
        return publishGps;
    }

    @Override
    public long getSecondsUntilNextEvent() {
        if (!isGpsPublish() || workflows == null) return -1;
        Instant now = clock.instant();
        long nearest = Long.MAX_VALUE;
        for (var entry : next.entrySet()) {
            String id = selected.get(entry.getKey());
            Workflow workflow = id == null ? null : workflows.apply(id);
            boolean visible = workflow != null && workflow.spawner().isGpsPublish();
            if (visible) {
                long millis = Math.max(0, Duration.between(now, entry.getValue()).toMillis());
                nearest = Math.min(nearest, (millis + 999) / 1000);
            }
        }
        return nearest == Long.MAX_VALUE ? -1 : nearest;
    }

    @Override
    public void tick() {
        if (workflows == null) return;
        Instant now = clock.instant();
        for (var entry : next.entrySet()) {
            if (entry.getValue().isAfter(now)) continue;
            String id = selected.get(entry.getKey());
            // Advance before launching, so a failing workflow cannot launch repeatedly.
            entry.setValue(entry.getKey().next(now, zone, false));
            selected.put(entry.getKey(), select(entry.getKey()));
            Workflow workflow = id == null ? null : workflows.apply(id);
            if (workflow != null) {
                try {
                    launch.accept(workflow.createSpawner());
                } catch (Exception e) {
                    log.error("Failed to start scheduled workflow {}", id, e);
                }
            }
        }
    }

    private String select(Schedule schedule) {
        var choices = schedules.get(schedule);
        double max = choices.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        var available = choices.entrySet().stream()
                .filter(choice -> workflows.apply(choice.getKey()) != null)
                .map(choice -> WeightedItem.of(choice.getValue() / max, choice.getKey())).toList();
        // Choose ahead of time so GPS describes the workflow that will actually launch.
        return available.isEmpty() ? null : new WeightedItemSelector<>(available).getRandomItem();
    }

    /** A bare time repeats daily; DAY HH:mm repeats weekly in the configured zone. */
    public record Schedule(DayOfWeek day, LocalTime time) {
        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
                .withResolverStyle(ResolverStyle.STRICT);

        public static Schedule parse(String value) {
            String[] parts = value.trim().split("\\s+");
            if (parts.length == 1) return new Schedule(null, LocalTime.parse(parts[0], TIME));
            if (parts.length == 2) return new Schedule(DayOfWeek.valueOf(parts[0].toUpperCase(Locale.ROOT)),
                    LocalTime.parse(parts[1], TIME));
            throw new IllegalArgumentException("Expected HH:mm or DAY HH:mm: " + value);
        }

        Instant next(Instant now, ZoneId zone, boolean inclusive) {
            LocalDate today = now.atZone(zone).toLocalDate();
            for (int offset = 0; offset <= 7; offset++) {
                LocalDate date = today.plusDays(offset);
                if (day != null && date.getDayOfWeek() != day) continue;
                // atZone moves a nonexistent DST time forward and chooses the earlier overlap.
                Instant candidate = date.atTime(time).atZone(zone).toInstant();
                if (candidate.isAfter(now) || inclusive && candidate.equals(now)) return candidate;
            }
            throw new IllegalStateException("No next occurrence for " + this);
        }
    }
}
