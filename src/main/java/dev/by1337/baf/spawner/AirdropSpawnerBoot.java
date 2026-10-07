package dev.by1337.baf.spawner;

import dev.by1337.baf.spawner.impl.TimerSpawner;
import dev.by1337.baf.spawner.global.ByTimeSpawner;
import dev.by1337.baf.spawner.global.GlobalSpawner;
import com.google.gson.JsonObject;
import java.util.Map;

public class AirdropSpawnerBoot {
    public static Map<String, JsonObject> schemas() {
        return Map.of("spawner:timer", TimerSpawner.WEB_SCHEMA.json("spawner:timer"));
    }
    public static void boot() {
        GlobalSpawner.REGISTRY.clear();
        GlobalSpawner.REGISTRY.register("default:by_time", ByTimeSpawner.DECODER);
        AirdropSpawner.REGISTRY.clear();
        AirdropSpawner.REGISTRY.register("spawner:timer", TimerSpawner.DECODER);
    }
}
