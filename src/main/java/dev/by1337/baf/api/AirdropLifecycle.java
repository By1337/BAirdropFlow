package dev.by1337.baf.api;

import dev.by1337.baf.component.NodeRegistry;
import dev.by1337.baf.generator.Generator;
import dev.by1337.baf.registry.DecoderRegistry;
import dev.by1337.baf.registry.SchemaRegistry;
import dev.by1337.baf.spawner.AirdropSpawner;
import dev.by1337.baf.spawner.global.GlobalSpawner;

public interface AirdropLifecycle {
    default void onLoad() {
    }

    default void componentsRegister(NodeRegistry registry) {
    }

    default void generatorsRegister(SchemaRegistry<Generator> registry) {
    }

    default void spawnersRegister(SchemaRegistry<AirdropSpawner> registry) {
    }

    default void globalSpawnersRegister(DecoderRegistry<GlobalSpawner> registry) {
    }

    default void onEnable() {
    }

    default void onDisable() {
    }
}
