package dev.by1337.baf.api.addon;

import dev.by1337.baf.BAirdropFlow;
import dev.by1337.baf.api.AirdropLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.Objects;

public abstract class AbstractAddon implements AirdropLifecycle {
    private BAirdropFlow plugin;
    private AddonDescription description;

    public final void initialize(BAirdropFlow plugin, AddonDescription description) {
        if (this.plugin != null) throw new IllegalStateException("Addon already initialized");
        this.plugin = Objects.requireNonNull(plugin);
        this.description = Objects.requireNonNull(description);
    }

    public final BAirdropFlow getPlugin() {
        return Objects.requireNonNull(plugin, "Addon is not initialized");
    }

    public final AddonDescription getDescription() {
        return Objects.requireNonNull(description, "Addon is not initialized");
    }

    public final Logger getLogger() {
        return LoggerFactory.getLogger("BAirdropFlow/" + getDescription().name());
    }

    public final File getDataFolder() {
        return new File(new File(getPlugin().getDataFolder(), "addons"), getDescription().name());
    }

    public final InputStream getResource(String name) {
        return getClass().getClassLoader().getResourceAsStream(name);
    }
}
