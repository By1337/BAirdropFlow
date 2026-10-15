package dev.by1337.baf.vote;

import dev.by1337.baf.entity.TickableEntity;
import dev.by1337.baf.launch.LaunchRegistry;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.plc.Placeholders;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

public final class VotingService implements AutoCloseable {
    private final Consumer<TickableEntity> launch;
    private final Consumer<String> warn;
    private final Consumer<Component> broadcast;
    private final Consumer<Session> closed;
    private Session active;
    private boolean stopped;

    public VotingService(Consumer<TickableEntity> launch, Consumer<String> warn,
                         Consumer<Component> broadcast, Consumer<Session> closed) {
        this.launch = launch;
        this.warn = warn;
        this.broadcast = broadcast;
        this.closed = closed;
    }

    public Session active() {
        return active;
    }

    public boolean start(VoteSpawner.Config config, LaunchRegistry registry) {
        if (stopped) return false;
        if (active != null) {
            warn.accept("Cannot start vote " + config.id() + ": vote " + active.config.id() + " is already active");
            return false;
        }
        registry.validate(config.id());
        Session session = new Session(config, registry);
        active = session;
        try {
            launch.accept(session);
            announce(session, config.messages().started());
            return true;
        } catch (RuntimeException | Error error) {
            if (active == session) active = null;
            session.removed = true;
            closed.accept(session);
            throw error;
        }
    }

    public boolean vote(UUID session, UUID player, String option) {
        if (active == null || !active.token.equals(session) || !active.config.options().containsKey(option))
            return false;
        active.ballots.put(player, option);
        return true;
    }

    private void announce(Session session, String message) {
        if (!message.isBlank()) broadcast.accept(MiniMessage.deserialize(session.text(message, null)));
    }

    private void finish(Session session) {
        if (active != session || session.removed) return;
        String winner = session.choose();
        session.winner = winner;
        session.removed = true;
        active = null;
        try {
            closed.accept(session);
            announce(session, winner == null ? session.config.messages().cancelled() : session.config.messages().ended());
        } finally {
            if (!stopped && winner != null) session.registry.start(winner);
        }
    }

    @Override
    public void close() {
        stopped = true;
        Session session = active;
        active = null;
        if (session != null) {
            session.removed = true;
            closed.accept(session);
        }
    }

    public final class Session implements TickableEntity {
        private final UUID token = UUID.randomUUID();
        private final VoteSpawner.Config config;
        private final LaunchRegistry registry;
        private final Map<UUID, String> ballots = new HashMap<>();
        private final int durationTicks;
        private int elapsed;
        private boolean removed;
        private String winner;

        private Session(VoteSpawner.Config config, LaunchRegistry registry) {
            this.config = config;
            this.registry = registry;
            durationTicks = (int) ((config.duration() + 49) / 50);
        }

        public UUID token() {
            return token;
        }

        public VoteSpawner.Config config() {
            return config;
        }

        public int timeLeft() {
            return (Math.max(0, durationTicks - elapsed) + 19) / 20;
        }

        public int count(String option) {
            return (int) ballots.values().stream().filter(option::equals).count();
        }

        public String selected(UUID player) {
            return ballots.getOrDefault(player, "");
        }

        public int total() {
            return ballots.size();
        }

        public String text(String text, UUID player) {
            return placeholders(player).setPlaceholders(text, this);
        }

        public dev.by1337.plc.PlaceholderResolver<Session> placeholders(UUID player) {
            return Placeholders.<Session>create()
                    .withContext("time_left", Session::timeLeft)
                    .withContext("vote_id", s -> config.id())
                    .withContext("total_votes", Session::total)
                    .withContext("winner", s -> config.options().getOrDefault(winner, ""))
                    .withContext("selected", s -> config.options().getOrDefault(player == null ? "" : selected(player), ""))
                    .of("votes", (option, s) -> count(option));
        }

        private String choose() {
            int max = config.options().keySet().stream().mapToInt(this::count).max().orElse(0);
            var tied = config.options().keySet().stream().filter(id -> count(id) == max).toList();
            VoteSpawner.Resolution policy = max == 0 ? config.noVotes() : config.tie();
            if (max != 0 && tied.size() == 1) return tied.getFirst();
            return switch (policy) {
                case CANCEL -> null;
                case FIRST -> tied.getFirst();
                case RANDOM -> tied.get(ThreadLocalRandom.current().nextInt(tied.size()));
            };
        }

        @Override
        public void tick() {
            if (!removed && ++elapsed >= durationTicks) finish(this);
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }
    }
}
