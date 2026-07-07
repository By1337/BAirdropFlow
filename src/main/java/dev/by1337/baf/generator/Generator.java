package dev.by1337.baf.generator;

import dev.by1337.baf.registry.DecoderRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface Generator {
    DecoderRegistry<Generator> REGISTRY = new DecoderRegistry<>();

    CompletableFuture<@Nullable Location> tryGenerate(World world);

}
