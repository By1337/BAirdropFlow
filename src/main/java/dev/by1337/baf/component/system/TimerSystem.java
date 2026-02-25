package dev.by1337.baf.component.system;

import dev.by1337.baf.AirdropManager;
import dev.by1337.baf.command.AirdropContext;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.impl.TimerComponent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

public class TimerSystem implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(TimerSystem.class);
    private final Plugin plugin;
    private final AirdropManager manager;
    private BukkitTask task;

    public TimerSystem(Plugin plugin, AirdropManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void start() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    private void tick() {
        manager.forEachComponents(TimerComponent.class, ((airdrop, components) -> {
            for (TimerComponent c : components) {
                try (var ctx = new AirdropContext(airdrop)){
                    ctx.call(c, TimerComponent::onTick);
                }
            }
        }));
    }

    private void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
