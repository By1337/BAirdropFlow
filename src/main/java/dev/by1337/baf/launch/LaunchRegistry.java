package dev.by1337.baf.launch;

import java.util.*;

/**
 * Main-thread registry. Publishers own their entries; replacing a publisher is atomic.
 */
public final class LaunchRegistry {
    private record Entry(Object owner, Startable event) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Set<String> starting = new HashSet<>();

    public void publish(Object owner, String id, Startable event) {
        Objects.requireNonNull(event);
        checkPublish(owner, id);
        unpublish(owner);
        entries.put(id, new Entry(owner, event));
    }

    public void checkPublish(Object owner, String id) {
        Objects.requireNonNull(owner);
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Empty launch ID");
        Entry old = entries.get(id);
        if (old != null && !old.owner.equals(owner)) throw new IllegalArgumentException("Duplicate launch ID: " + id);
    }

    public void unpublish(Object owner) {
        entries.values().removeIf(entry -> entry.owner.equals(owner));
    }

    public Startable get(String id) {
        var entry = entries.get(id);
        return entry == null ? null : entry.event;
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(entries.keySet()));
    }

    public void start(String id) {
        Startable event = get(id);
        if (event == null) throw new IllegalArgumentException("Unknown launch ID: " + id);
        if (!starting.add(id)) throw new IllegalStateException("Recursive launch: " + id);
        try {
            event.start();
        } finally {
            starting.remove(id);
        }
    }

    public void validate(String id) {
        validate(id, new HashSet<>(), new HashSet<>());
    }

    private void validate(String id, Set<String> path, Set<String> checked) {
        if (checked.contains(id)) return;
        Startable event = get(id);
        if (event == null) throw new IllegalArgumentException("Unknown launch ID: " + id);
        if (!path.add(id)) throw new IllegalArgumentException("Launch cycle: " + path + " -> " + id);
        for (String child : event.dependencies()) validate(child, path, checked);
        path.remove(id);
        checked.add(id);
    }
}
