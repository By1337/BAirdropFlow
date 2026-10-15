package dev.by1337.baf.vote;

import dev.by1337.baf.entity.TickableEntity;
import dev.by1337.baf.launch.LaunchRegistry;
import dev.by1337.baf.spawner.global.SpawnerContext;
import dev.by1337.yaml.YamlMap;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class VotingServiceTest {
    private final LaunchRegistry registry = new LaunchRegistry();
    private final List<TickableEntity> tasks = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<VotingService.Session> closed = new ArrayList<>();
    private final VotingService voting = new VotingService(tasks::add, warnings::add, message -> {}, closed::add);
    private final AtomicInteger a = new AtomicInteger(), b = new AtomicInteger();

    private VoteSpawner publish(String id, VoteSpawner.Resolution tie, VoteSpawner.Resolution empty) {
        registry.publish("event:a", "event:a", a::incrementAndGet);
        registry.publish("event:b", "event:b", b::incrementAndGet);
        Map<String, String> options = new LinkedHashMap<>(); options.put("event:a", "A"); options.put("event:b", "B");
        var config = new VoteSpawner.Config(id, 100, "baf:vote", options, tie, empty, true,
                new VoteSpawner.Messages("", "", "", ""));
        var spawner = new VoteSpawner(config);
        registry.publish(id, id, spawner);
        spawner.start(new SpawnerContext(registry, voting));
        return spawner;
    }

    @Test void uuidBallotsCanChangeAndOnlyWinnerStartsAfterFullDuration() {
        publish("vote", VoteSpawner.Resolution.RANDOM, VoteSpawner.Resolution.RANDOM);
        registry.start("vote"); var session = voting.active(); UUID player = UUID.randomUUID();
        assertEquals(1, tasks.size()); assertTrue(voting.vote(session.token(), player, "event:a"));
        assertTrue(voting.vote(session.token(), player, "event:b"));
        assertEquals(1, session.total()); assertEquals(0, session.count("event:a")); assertEquals(1, session.count("event:b"));
        assertFalse(voting.vote(UUID.randomUUID(), player, "event:a"));
        assertFalse(voting.vote(session.token(), player, "missing"));
        assertEquals("B 1 1", session.text("{selected} {votes:event:b} {total_votes}", player));
        session.tick(); assertSame(session, voting.active()); assertEquals(0, b.get());
        session.tick(); assertNull(voting.active()); assertTrue(session.isRemoved()); assertEquals(1, b.get());
        assertEquals(List.of(session), closed);
        session.tick(); assertEquals(1, b.get()); assertFalse(voting.vote(session.token(), player, "event:a"));
    }

    @Test void onlyOneGlobalVoteCanRunAndARejectedStartDoesNotChangeIt() {
        publish("first", VoteSpawner.Resolution.FIRST, VoteSpawner.Resolution.FIRST);
        publish("second", VoteSpawner.Resolution.FIRST, VoteSpawner.Resolution.FIRST);
        registry.start("first"); var first = voting.active(); registry.start("second");
        assertSame(first, voting.active()); assertEquals(1, tasks.size());
        assertEquals(1, warnings.size()); assertTrue(warnings.getFirst().contains("first")); assertTrue(warnings.getFirst().contains("second"));
        first.tick(); first.tick(); registry.start("second");
        assertNotSame(first, voting.active()); assertEquals(2, tasks.size());
        assertFalse(voting.vote(first.token(), UUID.randomUUID(), "event:a"));
    }

    @Test void tieAndNoVotesPoliciesAreRespectedAndShutdownNeverStartsAWinner() {
        publish("vote", VoteSpawner.Resolution.CANCEL, VoteSpawner.Resolution.FIRST);
        registry.start("vote"); var empty = voting.active(); empty.tick(); empty.tick(); assertEquals(1, a.get());
        registry.start("vote"); var tied = voting.active();
        voting.vote(tied.token(), UUID.randomUUID(), "event:a"); voting.vote(tied.token(), UUID.randomUUID(), "event:b");
        tied.tick(); tied.tick(); assertNull(voting.active()); assertEquals(1, a.get()); assertEquals(0, b.get());
        registry.start("vote"); var cancelled = voting.active(); voting.close();
        cancelled.tick(); cancelled.tick(); assertTrue(cancelled.isRemoved()); assertNull(voting.active()); assertEquals(1, a.get());
        registry.start("vote"); assertNull(voting.active());
    }

    @Test void voteCanStartAnotherVoteWhenWinnerIsAlsoRegistered() {
        publish("second", VoteSpawner.Resolution.FIRST, VoteSpawner.Resolution.CANCEL);
        var first = new VoteSpawner(new VoteSpawner.Config("first", 50, "baf:vote", Map.of("second", "Next"),
                VoteSpawner.Resolution.FIRST, VoteSpawner.Resolution.FIRST, true, new VoteSpawner.Messages("", "", "", "")));
        registry.publish("first", "first", first); first.start(new SpawnerContext(registry, voting));
        registry.start("first"); voting.active().tick();
        assertNotNull(voting.active()); assertEquals("second", voting.active().config().id()); assertTrue(warnings.isEmpty());
    }

    @Test void invalidConfigsAreRejectedAndYamlLoadsNamesAndPolicies() {
        for (String fields : List.of("duration: 0s", "duration: 2h", "options: {}", "tie: bad")) {
            var yaml = YamlMap.loadFromString("id: vote\noptions: { 'event:a': A }\n" + fields);
            assertTrue(VoteSpawner.DECODER.decode(yaml.get()).hasError(), fields);
        }
        var decoded = VoteSpawner.DECODER.decode(YamlMap.loadFromString("id: vote\noptions: { 'event:a': A }\nno_votes: cancel").get()).getOrThrow();
        assertEquals(Set.of("event:a"), decoded.dependencies()); assertEquals(VoteSpawner.Resolution.CANCEL, decoded.config().noVotes());
    }
}
