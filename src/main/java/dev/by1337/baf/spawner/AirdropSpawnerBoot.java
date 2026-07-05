package dev.by1337.baf.spawner;

import dev.by1337.baf.spawner.impl.TimerSpawner;

public class AirdropSpawnerBoot {
    public static void boot() {
        AirdropSpawner.REGISTRY.clear();
        AirdropSpawner.REGISTRY.register("spawner:timer", TimerSpawner.DECODER);
    }
}
