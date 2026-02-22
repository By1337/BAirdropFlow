package dev.by1337.baf;

import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.interact.InteractManager;
import dev.by1337.baf.loot.LootTables;
import dev.by1337.baf.util.FileBackuper;
import dev.by1337.yaml.YamlMap;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public class AirdropManager {
    private static final Logger log = LoggerFactory.getLogger(AirdropManager.class);
    private final List<Airdrop> airdrops = new CopyOnWriteArrayList<>();
    private final Plugin plugin;
    private LootTables lootTables;
    private final InteractManager interactManager;

    public AirdropManager(Plugin plugin) {
        this.plugin = plugin;
        interactManager = new InteractManager(plugin);
        loadLootTables();
    }
    @ApiStatus.Internal
    void enable(){
        interactManager.enable();
    }
    @ApiStatus.Internal
    void disable(){
        interactManager.disable();
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
        airdrop.cleaner().cleanup();
    }

    public <T extends AirdropComponent> void forEachComponents(Class<T> t, BiConsumer<Airdrop, List<T>> c) {
        for (Airdrop airdrop : airdrops) {
            var list = airdrop.components().allOf(t);
            if (list.isEmpty()) continue;
            c.accept(airdrop, list);
        }
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
}
