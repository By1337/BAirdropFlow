package dev.by1337.baf.generator;

import dev.by1337.baf.generator.impl.FixedGenerator;

public class Generators {

    public static void boot() {
        Generator.REGISTRY.clear();
        Generator.REGISTRY.register("generator:fixed", FixedGenerator.DECODER);
    }
}
