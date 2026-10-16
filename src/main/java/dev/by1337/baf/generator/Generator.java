package dev.by1337.baf.generator;

import dev.by1337.baf.registry.SchemaRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface Generator {
    SchemaRegistry<Generator> REGISTRY = new SchemaRegistry<>();

    CompletableFuture<@Nullable Location> tryGenerate(World world);

}
