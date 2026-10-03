package dev.by1337.baf.component.system;

import dev.by1337.baf.AirdropManager;
import dev.by1337.baf.BAF;
import dev.by1337.edsl.context.EventContext;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

public class AsyncTimerSystem implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(AsyncTimerSystem.class);
    private final Plugin plugin;
    private final AirdropManager manager;
    private BukkitTask task;

    public AsyncTimerSystem(Plugin plugin, AirdropManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public AsyncTimerSystem start() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 0, 1);
        return this;
    }

    private void tick() {
        manager.forEachAirdrops(airdrop -> airdrop.components().forEach(c -> {
            if (c instanceof Tickable t) {
                t.onAsyncTick();
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

    @FunctionalInterface
    public interface Tickable{
        void onAsyncTick();
    }
}
