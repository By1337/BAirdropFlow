package dev.by1337.baf.launch;

import java.util.Set;

/**
 * A configured event that can be started by ID without knowing its implementation.
 */
public interface Startable {
    void start();

    default boolean isGpsPublish() {
        return false;
    }

    default Set<String> dependencies() {
        return Set.of();
    }
}
