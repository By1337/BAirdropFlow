package dev.by1337.baf.component.system;

import dev.by1337.baf.AirdropManager;
import dev.by1337.baf.BAF;
import dev.by1337.baf.component.impl.ParticleComponent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

public class ParticleSystem implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(ParticleSystem.class);
    private final Plugin plugin;
    private final AirdropManager manager;
    private BukkitTask task;

    public ParticleSystem(Plugin plugin, AirdropManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        if (task != null && !task.isCancelled()) task.cancel();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void tick() {
        manager.forEachAirdrops(airdrop -> airdrop.components().forEach(component -> {
            if (component instanceof ParticleComponent particle && !particle.isRemoved()) {
                try (var ctx = BAF.newContext(airdrop)) {
                    try {
                        particle.onTick(ctx);
                    } catch (Exception e) {
                        log.error("Failed to tick particle component {}", component.name(), e);
                        if (!particle.isRemoved()) particle.markRemoved(ctx);
                    }
                }
            }
        }));
    }

    @Override
    public void close() {
        if (task != null && !task.isCancelled()) task.cancel();
        task = null;
    }
}
