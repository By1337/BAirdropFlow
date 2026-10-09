package dev.by1337.baf.spawner.global;

import dev.by1337.baf.AirdropManager;
import dev.by1337.baf.BAF;
import dev.by1337.baf.Workflow;
import dev.by1337.baf.workflow.WorkflowRepository;
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
    private WorkflowRepository workflows;
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
        HandlerList.unregisterAll(this);
        items.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        var item = items.getByItem(event.getItem());
        if (item == null || event.getClickedBlock() == null) return;
        var who = event.getPlayer();
        event.setCancelled(true);
        Workflow workflow = workflows.getById(item.workflow());
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

    public void load(Path directory, WorkflowRepository workflows, AirdropManager manager,
                     Consumer<String> logger) throws IOException {
        this.workflows = workflows;
        this.manager = manager;
        Files.createDirectories(directory);
        try (var files = Files.walk(directory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .sorted().toList()) {
                try {
                    GlobalSpawner spawner = GlobalSpawner.REGISTRY.decoder
                            .decode(YamlMap.load(file.toFile()).get()).getOrThrow();
                    spawner.start(workflows::getById, manager::addTicking);
                    if (spawner instanceof ByItemSpawner byItem) {
                        for (String id : byItem.items().ids()) {
                            if (items.byId(id) != null)
                                throw new IllegalArgumentException("Duplicate spawn item: " + id);
                        }
                        byItem.items().idToItem().forEach(items::register);
                    }
                    spawners.add(spawner);
                    manager.addTicking(spawner);
                } catch (Exception e) {
                    logger.accept(file + ": " + e.getMessage());
                }
            }
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
