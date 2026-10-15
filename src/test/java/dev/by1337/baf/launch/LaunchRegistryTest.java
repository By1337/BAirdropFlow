package dev.by1337.baf.launch;

import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LaunchRegistryTest {
    @Test void publishersReplaceTheirOwnIdWithoutOverwritingOtherPublishers() {
        var registry = new LaunchRegistry(); var calls = new AtomicInteger();
        registry.publish("workflow.yml", "event:old", calls::incrementAndGet);
        registry.start("event:old"); assertEquals(1, calls.get());
        registry.publish("vote.yml", "event:vote", () -> {});
        assertThrows(IllegalArgumentException.class, () -> registry.publish("workflow.yml", "event:vote", () -> fail()));
        assertNotNull(registry.get("event:old"));
        registry.publish("workflow.yml", "event:new", calls::incrementAndGet);
        assertNull(registry.get("event:old")); registry.start("event:new"); assertEquals(2, calls.get());
        registry.unpublish("workflow.yml"); assertEquals(Set.of("event:vote"), registry.ids());
        assertThrows(IllegalArgumentException.class, () -> registry.start("event:new"));
    }
    @Test void detectsMissingDependenciesCyclesAndRecursiveRuntimeStarts() {
        var registry = new LaunchRegistry();
        registry.publish("one", "one", new Startable() {
            public void start() { registry.start("one"); }
            public Set<String> dependencies() { return Set.of("two"); }
        });
        assertThrows(IllegalArgumentException.class, () -> registry.validate("one"));
        registry.publish("two", "two", new Startable() {
            public void start() {}
            public Set<String> dependencies() { return Set.of("one"); }
        });
        assertThrows(IllegalArgumentException.class, () -> registry.validate("one"));
        assertThrows(IllegalStateException.class, () -> registry.start("one"));
        registry.publish("one", "one", () -> {});
        assertDoesNotThrow(() -> registry.start("one"));
        assertDoesNotThrow(() -> registry.validate("two"));
    }
}
