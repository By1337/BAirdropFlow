package dev.by1337.baf.spawner;

import dev.by1337.baf.spawner.impl.TimerSpawner;
import com.google.gson.JsonObject;
import java.util.Map;

public class AirdropSpawnerBoot {
    public static Map<String, JsonObject> schemas() {
        return Map.of("spawner:timer", TimerSpawner.WEB_SCHEMA.json("spawner:timer"));
    }
    public static void boot() {
        AirdropSpawner.REGISTRY.clear();
        AirdropSpawner.REGISTRY.register("spawner:timer", TimerSpawner.DECODER);
    }
}
