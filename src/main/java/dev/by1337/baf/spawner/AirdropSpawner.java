package dev.by1337.baf.spawner;

import dev.by1337.baf.Workflow;
import dev.by1337.baf.entity.TickableEntity;
import dev.by1337.baf.registry.SchemaRegistry;

public interface AirdropSpawner {
    SchemaRegistry<AirdropSpawner> REGISTRY = new SchemaRegistry<>();

    TickableEntity create(Workflow workflow);

    boolean isGpsPublish();
}
