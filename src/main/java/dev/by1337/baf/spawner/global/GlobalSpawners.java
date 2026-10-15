package dev.by1337.baf.spawner.global;

import dev.by1337.baf.AirdropManager;
import dev.by1337.baf.BAF;
import dev.by1337.baf.Workflow;
import dev.by1337.baf.launch.LaunchRegistry;
import dev.by1337.baf.vote.VoteSpawner;
import dev.by1337.baf.vote.VotingService;
import dev.by1337.baf.vote.VoteMenu;
import dev.by1337.bmenu.BMenu;
import dev.by1337.item.registry.GlobalItemRegistry;
import dev.by1337.item.registry.ItemRegistry;
import dev.by1337.plc.PapiResolver;
import dev.by1337.yaml.YamlMap;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GlobalSpawners implements Listener, AutoCloseable {
    private final ItemRegistry<ByItemSpawner.SpawnItem> items = new ItemRegistry<>(ByItemSpawner.SPACE);
    private LaunchRegistry launches;
    private VotingService voting;
    private final List<Object> publishers = new ArrayList<>();
    public VotingService voting() { return voting; }
    private AirdropManager manager;

    public ItemRegistry<ByItemSpawner.SpawnItem> items() {
        return items;
    }

    public void enableItems(Plugin plugin) {
        GlobalItemRegistry.register(items);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void close() {
        if (voting != null) voting.close();
        if (launches != null) publishers.forEach(launches::unpublish);
        publishers.clear();
        HandlerList.unregisterAll(this);
        items.clear();
        spawners.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var item = items.getByItem(event.getItem());
        if (item == null || event.getClickedBlock() == null) return;
        var who = event.getPlayer();
        event.setCancelled(true);
        var target = launches.get(item.workflow());
        Workflow workflow = target instanceof Workflow w ? w : null;
        if (workflow == null) {
            BAF.sendMessage("item_spawn_workflow", event.getPlayer());
            return;
        }
        String failure = item.requirements().failure(event.getClickedBlock());
        if (failure != null) {
            BAF.sendMessage("item_spawn_" + failure, event.getPlayer());
            return;
        }
        var air = workflow.make(event.getClickedBlock().getLocation().add(0, 1, 0));
        item.args().forEach((k, v) -> air.args().put(k, PapiResolver.get().setPlaceholders(v, who)));
        manager.addAirdrop(air);
        if (item.consume()) event.getItem().setAmount(event.getItem().getAmount() - 1);
    }

    private final List<GlobalSpawner> spawners = new ArrayList<>();

    public void load(Path directory, LaunchRegistry launches, AirdropManager manager,
                     Consumer<String> logger) throws IOException {
        this.launches = launches;
        this.manager = manager;
        voting = new VotingService(manager::addTicking, logger, Bukkit::broadcast, session -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                var menu = BMenu.menuLoader().getOpenedMenu(player);
                if (menu instanceof VoteMenu vote && vote.session() == session) menu.close();
            }
        });
        Files.createDirectories(directory);
        record Loaded(Path file, GlobalSpawner spawner, Object owner) {}
        List<Loaded> loaded = new ArrayList<>();
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml")).sorted().toList()) {
                try {
                    GlobalSpawner spawner = GlobalSpawner.REGISTRY.decoder.decode(YamlMap.load(file.toFile()).get()).getOrThrow();
                    Object owner = new Object();
                    if (spawner instanceof VoteSpawner vote) {
                        launches.publish(owner, vote.id(), vote);
                        publishers.add(owner);
                    }
                    loaded.add(new Loaded(file, spawner, owner));
                } catch (Exception e) { logger.accept(file + ": " + e.getMessage()); }
            }
        }
        // Remove invalid definitions and their dependents before enabling any schedules.
        boolean changed;
        do {
            changed = false;
            var iterator = loaded.iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (!(entry.spawner instanceof VoteSpawner vote)) continue;
                try { launches.validate(vote.id()); }
                catch (IllegalArgumentException error) {
                    logger.accept(entry.file + ": " + error.getMessage());
                    launches.unpublish(entry.owner); publishers.remove(entry.owner);
                    iterator.remove(); changed = true;
                }
            }
        } while (changed);
        // Publish every vote before resolving references, regardless of file order.
        var context = new SpawnerContext(launches, voting);
        for (Loaded entry : loaded) {
            try {
                entry.spawner.start(context);
                if (entry.spawner instanceof ByItemSpawner byItem) {
                    for (String id : byItem.items().ids())
                        if (items.byId(id) != null) throw new IllegalArgumentException("Duplicate spawn item: " + id);
                    byItem.items().idToItem().forEach(items::register);
                }
                spawners.add(entry.spawner);
                manager.addTicking(entry.spawner);
            } catch (Exception e) { logger.accept(entry.file + ": " + e.getMessage()); }
        }
    }

    public List<GlobalSpawner> all() {
        return List.copyOf(spawners);
    }

    public long getSecondsUntilNextEvent() {
        return spawners.stream().filter(s -> !s.isRemoved() && s.isGpsPublish())
                .mapToLong(GlobalSpawner::getSecondsUntilNextEvent).filter(seconds -> seconds >= 0)
                .min().orElse(-1);
    }
}
