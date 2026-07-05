package dev.by1337.baf.generator.impl;

import dev.by1337.baf.generator.Generator;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public class FixedGenerator implements Generator {
    public static final YamlDecoder<FixedGenerator> DECODER = RecordYamlDecoder.mapOf(
            FixedGenerator::new,
            Vec3i.DECODER.fieldOf("at", new Vec3i(0, 100, 0))
    );
    private final Vec3i pos;

    public FixedGenerator(Vec3i pos) {
        this.pos = pos;
    }

    @Override
    public @Nullable Location tryGenerate(World world) {
        return new Location(world, pos.x(), pos.y(), pos.z());
    }
}
