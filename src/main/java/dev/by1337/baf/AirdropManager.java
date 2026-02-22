package dev.by1337.baf;

import dev.by1337.baf.component.AirdropComponent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public class AirdropManager {
    private static final Logger log = LoggerFactory.getLogger(AirdropManager.class);
    private final List<Airdrop> airdrops = new CopyOnWriteArrayList<>();
    private final Plugin plugin;
    private BukkitTask task;

    public AirdropManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void addAirdrop(Airdrop airdrop) {
        if (airdrops.contains(airdrop)) {
            throw new IllegalStateException("airdrop already exist!");
        }
        airdrops.add(airdrop);
        var init = airdrop.config().nodes().get("__init__");
        if (init == null) {
            log.error("Airdrop {} has no __init__ node!", airdrop);
            airdrop.markRemoved();
            return;
        }
        var c = init.create(airdrop, "__init__");
        airdrop.components().add(c);
    }

    public void removeAirdrop(Airdrop airdrop) {
        if (!airdrops.remove(airdrop)) {
            throw new IllegalArgumentException("Airdrop " + airdrop + " is not registered!");
        }
        var destroy = airdrop.config().nodes().get("__destroy__");
        if (destroy == null) {
            log.error("Airdrop {} has no __destroy__ node!", airdrop);
        } else {
            var c = destroy.create(airdrop, "__destroy__");
            airdrop.components().add(c);
        }
        for (AirdropComponent component : airdrop.components().all()) {
            component.markRemoved();
        }
    }

    void start() {
        if (task != null) {
            task.cancel();
        }

    }

    public <T extends AirdropComponent> void forEachComponents(Class<T> t, BiConsumer<Airdrop, List<T>> c) {
        for (Airdrop airdrop : airdrops) {
            var list = airdrop.components().allOf(t);
            if (list.isEmpty()) continue;
            c.accept(airdrop, list);
        }
    }

}
