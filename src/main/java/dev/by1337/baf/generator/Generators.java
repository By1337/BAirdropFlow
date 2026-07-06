package dev.by1337.baf.generator;

import dev.by1337.baf.generator.impl.FixedGenerator;
import dev.by1337.baf.generator.impl.RandomGenerator;

public class Generators {

    public static void boot() {
        Generator.REGISTRY.clear();
        Generator.REGISTRY.register("generator:fixed", FixedGenerator.DECODER);
        Generator.REGISTRY.register("generator:random", RandomGenerator.DECODER);
    }
}
