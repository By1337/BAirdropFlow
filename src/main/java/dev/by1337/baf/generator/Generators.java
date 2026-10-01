package dev.by1337.baf.generator;

import dev.by1337.baf.generator.impl.FixedGenerator;
import dev.by1337.baf.generator.impl.RandomGenerator;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

public class Generators {

    public static Map<String, JsonObject> schemas() {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        result.put("generator:fixed", FixedGenerator.WEB_SCHEMA.json("generator:fixed"));
        result.put("generator:random", RandomGenerator.WEB_SCHEMA.json("generator:random"));
        return result;
    }

    public static void boot() {
        Generator.REGISTRY.clear();
        Generator.REGISTRY.register("generator:fixed", FixedGenerator.DECODER);
        Generator.REGISTRY.register("generator:random", RandomGenerator.DECODER);
    }
}
