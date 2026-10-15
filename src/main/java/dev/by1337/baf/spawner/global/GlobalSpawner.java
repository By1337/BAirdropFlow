package dev.by1337.baf.spawner.global;

import dev.by1337.baf.entity.TickableEntity;
import dev.by1337.baf.registry.DecoderRegistry;


public interface GlobalSpawner extends TickableEntity {
    DecoderRegistry<GlobalSpawner> REGISTRY = new DecoderRegistry<>();

    void start(SpawnerContext context);

    boolean isGpsPublish();

    /**
     * Seconds until the next GPS-published workflow spawner starts, or -1 if unavailable.
     */
    long getSecondsUntilNextEvent();

    @Override
    default boolean isRemoved() {
        return false;
    }
}
