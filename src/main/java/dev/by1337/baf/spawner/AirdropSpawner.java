package dev.by1337.baf.spawner;

import dev.by1337.baf.Workflow;
import dev.by1337.baf.entity.TickableEntity;
import dev.by1337.baf.registry.DecoderRegistry;

public interface AirdropSpawner {
    DecoderRegistry<AirdropSpawner> REGISTRY = new DecoderRegistry<>();

    TickableEntity create(Workflow workflow);
}
