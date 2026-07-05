package dev.by1337.baf;

import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.config.Config;
import dev.by1337.baf.entity.TickableEntity;
import dev.by1337.baf.interact.InteractManager;
import dev.by1337.baf.loot.LootTables;
import dev.by1337.baf.util.FileBackuper;
import dev.by1337.edsl.EventContextFactory;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.plc.PlaceholderResolver;
import dev.by1337.yaml.YamlMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class AirdropManager {
    private static final Logger log = LoggerFactory.getLogger(AirdropManager.class);
    private final List<Airdrop> airdrops = new CopyOnWriteArrayList<>();
    private final Plugin plugin;
    private LootTables lootTables;
    private final InteractManager interactManager;
    private final Config config;
    private BukkitTask ticker;
    private final List<TickableEntity> ticking = new CopyOnWriteArrayList<>();

    public AirdropManager(Plugin plugin, Config config) {
        this.plugin = plugin;
        interactManager = new InteractManager(plugin);
        this.config = config;
        loadLootTables();
    }

    @ApiStatus.Internal
    void enable() {
        interactManager.enable();
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    @ApiStatus.Internal
    void disable() {
        interactManager.disable();
        if (ticker != null) {
            ticker.cancel();
        }
        ticking.clear();
        for (Airdrop airdrop : airdrops) {
            removeAirdrop(airdrop, newContext());
        }
    }

    private void tick() {
        for (TickableEntity v : ticking) {
            if (v.isRemoved()) {
                ticking.remove(v);
            } else {
                try {
                    v.tick();
                } catch (Exception e) {
                    log.error("Failed to tick {}", v, e);
                    ticking.remove(v);
                }
            }
        }
    }

    public void addTicking(TickableEntity e) {
        ticking.add(e);
    }

    private void loadLootTables() {
        File file = new File(plugin.getDataFolder(), "loot.yml");
        if (file.exists()) {
            try {
                lootTables = YamlMap.load(file).get().decode(LootTables.CODEC).getOrThrow();
            } catch (Exception e) {
                log.error("Failed to load loot tables! Backed up to {}",
                        FileBackuper.backup(file, new File(plugin.getDataFolder(), "backups")),
                        e
                );
                lootTables = LootTables.createDefault();
            }
        } else {
            lootTables = LootTables.createDefault();
            saveLootTables();
        }
    }

    public void saveLootTables() {
        try {
            File file = new File(plugin.getDataFolder(), "loot.yml");
            var s = LootTables.CODEC.encode(lootTables).asYamlMap().getOrThrow().saveToString();
            Files.writeString(file.toPath(), s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to save loot tables!", e);
        }
    }

    public void addAirdrop(Airdrop airdrop) {
        if (airdrops.contains(airdrop)) {
            throw new IllegalStateException("airdrop already exist!");
        }
        airdrops.add(airdrop);
        var init = airdrop.config().nodes().get("__init__");
        if (init == null) {
            log.error("Airdrop {} has no __init__ node!", airdrop);
            try (var ctx = BAF.newContext(airdrop)) {
                airdrop.markRemoved(ctx);
            }
            return;
        }
        var c = init.create(airdrop, "__init__");
        try (var ctx = BAF.newContext(airdrop)) {
            airdrop.components().add(c, ctx);
        }
    }

    public void removeAirdrop(Airdrop airdrop, EventContext ctx) {
        if (!airdrops.remove(airdrop)) {
            throw new IllegalArgumentException("Airdrop " + airdrop + " is not registered!");
        }
        var destroy = airdrop.config().nodes().get("__destroy__");
        if (destroy == null) {
            log.error("Airdrop {} has no __destroy__ node!", airdrop);
        } else {
            var c = destroy.create(airdrop, "__destroy__");
            airdrop.components().add(c, ctx);
        }
        for (AirdropComponent component : airdrop.components().all()) {
            component.markRemoved(ctx);
        }
        airdrop.cleaner().cleanup();
    }

    public void forEachAirdrops(Consumer<Airdrop> c) {
        airdrops.forEach(c);
    }

    public LootTables lootTables() {
        return lootTables;
    }

    public InteractManager interactManager() {
        return interactManager;
    }

    public Plugin plugin() {
        return plugin;
    }


    public EventContext newContext(PlaceholderResolver<EventContext> p) {
        return config.eventCtx.newContext().placeholders(p).build();
    }

    public EventContext newContext() {
        return config.eventCtx.newContext().build();
    }

    public EventContextFactory.Builder newContextBuilder(final Object... args) {
        return config.eventCtx.newContext(args);
    }


    public EventContext newContext(@Nullable Player player) {
        return config.eventCtx.newContext().source(player).build();
    }

    public EventContext newContext(Airdrop airdrop) {
        return config.eventCtx.newContext(airdrop).placeholders(Airdrop.PLACEHOLDERS.bindCtx(airdrop)).build();
    }

    public EventContext newContext(Airdrop airdrop, @Nullable Player player) {
        return config.eventCtx.newContext(airdrop).source(player).placeholders(Airdrop.PLACEHOLDERS.bindCtx(airdrop)).build();
    }
}
