package dev.by1337.baf.vote;

import dev.by1337.baf.launch.Startable;
import dev.by1337.baf.spawner.global.GlobalSpawner;
import dev.by1337.baf.spawner.global.SpawnerContext;
import dev.by1337.baf.util.time.DurationParser;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;

import java.util.*;

public final class VoteSpawner implements GlobalSpawner, Startable {
    public enum Resolution {RANDOM, FIRST, CANCEL}

    public record Messages(String started, String ended, String voted, String cancelled) {
        static final YamlDecoder<Messages> DECODER = RecordYamlDecoder.mapOf(Messages::new,
                YamlDecoder.STRING.fieldOf("started", "<gold>Выберите следующее событие: <click:run_command:'/vote'><green>[/vote]</green></click> <gray>Осталось {time_left} сек."),
                YamlDecoder.STRING.fieldOf("ended", "<gold>Голосование завершено. Победитель: <yellow>{winner}"),
                YamlDecoder.STRING.fieldOf("voted", "<green>Ваш голос: <yellow>{selected}"),
                YamlDecoder.STRING.fieldOf("cancelled", "<gray>Голосование завершено без запуска события."));
    }

    public record Config(String id, long duration, String menu, Map<String, String> options,
                         Resolution tie, Resolution noVotes, boolean publishGps, Messages messages) {
        private static final YamlDecoder<Resolution> RESOLUTION = YamlDecoder.STRING.map(s -> Resolution.valueOf(s.toUpperCase(Locale.ROOT)));
        static final YamlDecoder<Config> DECODER = RecordYamlDecoder.mapOf(Config::new,
                YamlDecoder.STRING.fieldOf("id"), DurationParser.MILLIS.fieldOf("duration", 60000L),
                YamlDecoder.STRING.fieldOf("menu", "baf:vote"), YamlDecoder.STRING_TO_STRING.fieldOf("options"),
                RESOLUTION.fieldOf("tie", Resolution.RANDOM), RESOLUTION.fieldOf("no_votes", Resolution.RANDOM),
                YamlDecoder.BOOL.fieldOf("publish_gps", true), Messages.DECODER.fieldOf("messages",
                        new Messages("<gold>Выберите событие: <click:run_command:'/vote'><green>[/vote]</green></click> <gray>{time_left} сек.",
                                "<gold>Победитель голосования: <yellow>{winner}", "<green>Ваш голос: <yellow>{selected}",
                                "<gray>Голосование завершено без запуска события.")));

        public Config {
            options = Collections.unmodifiableMap(new LinkedHashMap<>(options));
            if (id.isBlank() || menu.isBlank() || duration < 50 || duration > 3600000 || options.isEmpty()
                    || options.size() > 54 || options.entrySet().stream().anyMatch(e -> e.getKey().isBlank() || e.getValue().isBlank()))
                throw new IllegalArgumentException("Invalid vote configuration");
        }
    }

    public static final YamlDecoder<VoteSpawner> DECODER = Config.DECODER.map(VoteSpawner::new);
    private final Config config;
    private SpawnerContext context;

    public VoteSpawner(Config config) {
        this.config = config;
    }

    public Config config() {
        return config;
    }

    public String id() {
        return config.id;
    }

    @Override
    public Set<String> dependencies() {
        return config.options.keySet();
    }

    @Override
    public void start(SpawnerContext context) {
        context.launches().validate(config.id);
        this.context = context;
    }

    @Override
    public void start() {
        if (context == null) throw new IllegalStateException("Vote is not initialized: " + id());
        context.voting().start(config, context.launches());
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean isGpsPublish() {
        return config.publishGps;
    }

    @Override
    public long getSecondsUntilNextEvent() {
        return -1;
    }
}
