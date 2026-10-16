package dev.by1337.baf.spawner;

import com.google.gson.JsonObject;
import dev.by1337.baf.spawner.global.ByItemSpawner;
import dev.by1337.baf.spawner.global.ByTimeSpawner;
import dev.by1337.baf.spawner.global.GlobalSpawner;
import dev.by1337.baf.spawner.impl.TimerSpawner;

import java.util.Map;

public class AirdropSpawnerBoot {
    public static Map<String, JsonObject> schemas() {
        return AirdropSpawner.REGISTRY.schemas();
    }

    public static void boot() {
        GlobalSpawner.REGISTRY.clear();
        GlobalSpawner.REGISTRY.register("default:by_time", ByTimeSpawner.DECODER);
        GlobalSpawner.REGISTRY.register("default:by_item", ByItemSpawner.DECODER);
        GlobalSpawner.REGISTRY.register("default:vote", dev.by1337.baf.vote.VoteSpawner.DECODER);
        AirdropSpawner.REGISTRY.clear();
        AirdropSpawner.REGISTRY.register("spawner:timer", TimerSpawner.DECODER, TimerSpawner.WEB_SCHEMA);
    }
}
