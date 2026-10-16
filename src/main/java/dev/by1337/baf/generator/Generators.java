package dev.by1337.baf.generator;

import com.google.gson.JsonObject;
import dev.by1337.baf.generator.impl.FixedGenerator;
import dev.by1337.baf.generator.impl.RandomGenerator;

import java.util.Map;

public class Generators {

    public static Map<String, JsonObject> schemas() {
        return Generator.REGISTRY.schemas();
    }

    public static void boot() {
        Generator.REGISTRY.clear();
        Generator.REGISTRY.register("generator:fixed", FixedGenerator.DECODER, FixedGenerator.WEB_SCHEMA);
        Generator.REGISTRY.register("generator:random", RandomGenerator.DECODER, RandomGenerator.WEB_SCHEMA);
    }
}
