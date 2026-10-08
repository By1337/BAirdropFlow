package dev.by1337.baf.loot.inventory;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class LootingInventory implements Listener, InventoryHolder {
    private final int DISTANCE_LIMIT_SQ = 6 * 6; // 4.5 блока ванильный лимит, сделаем 6
    private final Inventory inventory;
    private final Set<UUID> viewers = new HashSet<>();
    private final Location center;

    private final Plugin plugin;
    private final int takeCooldownTicks;
    private final List<Material> obfuscationMaterials;
    private final Component obfuscationName;
    private final NamespacedKey maskKey;
    private final Map<String, ItemStack> hiddenItems = new HashMap<>();
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private BukkitTask fillTask;
    private boolean closed;

    public LootingInventory(int size, @NotNull Component title, Location center, Plugin plugin) {
        this(size, title, center, plugin, -1, List.of(), null);
    }

    public LootingInventory(int size, @NotNull Component title, Location center, Plugin plugin,
                            int takeCooldownTicks, List<Material> obfuscationMaterials, Component obfuscationName) {
        if (takeCooldownTicks < -1) throw new IllegalArgumentException("Invalid take cooldown");
        if (obfuscationMaterials.stream().anyMatch(m -> !m.isItem() || m.isAir()))
            throw new IllegalArgumentException("Mask materials must be non-air items");
        this.plugin = plugin;
        this.takeCooldownTicks = takeCooldownTicks;
        this.obfuscationMaterials = List.copyOf(obfuscationMaterials);
        this.obfuscationName = obfuscationName;
        this.maskKey = new NamespacedKey(plugin, "loot_mask");
        this.center = center;
        this.inventory = Bukkit.createInventory(this, size, title);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void fill(List<ItemStack> list) {
        fill(list, 0);
    }

    /** Every stack gets one insertion attempt; a full inventory discards that attempt. */
    public void fill(List<ItemStack> list, int durationTicks) {
        if (durationTicks < 0) throw new IllegalArgumentException("Invalid fill duration");
        if (fillTask != null) {
            fillTask.cancel();
            fillTask = null;
        }
        if (closed) return;
        List<ItemStack> items = list.stream().filter(item -> !empty(item)).map(ItemStack::clone).toList();
        if (durationTicks == 0) {
            items.forEach(this::insert);
            return;
        }
        if (items.isEmpty()) return;
        fillTask = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            private int elapsed;
            private int inserted;

            @Override
            public void run() {
                int due = (int) ((long) ++elapsed * items.size() / durationTicks);
                while (inserted < due) insert(items.get(inserted++));
                if (elapsed >= durationTicks) {
                    fillTask.cancel();
                    fillTask = null;
                }
            }
        }, 1, 1);
    }

    private void insert(ItemStack item) {
        List<Integer> free = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (empty(inventory.getItem(slot))) free.add(slot);
        }
        if (free.isEmpty()) return;
        ItemStack displayed = item;
        if (!obfuscationMaterials.isEmpty()) {
            String id = UUID.randomUUID().toString();
            hiddenItems.put(id, item);
            displayed = new ItemStack(obfuscationMaterials.get(ThreadLocalRandom.current().nextInt(obfuscationMaterials.size())));
            var meta = displayed.getItemMeta();
            if (obfuscationName != null) meta.displayName(obfuscationName);
            meta.getPersistentDataContainer().set(maskKey, PersistentDataType.STRING, id);
            displayed.setItemMeta(meta);
        }
        inventory.setItem(free.get(ThreadLocalRandom.current().nextInt(free.size())), displayed);
        // Items arriving during an active cooldown must also show its remaining duration.
        for (HumanEntity viewer : inventory.getViewers()) {
            if (viewer instanceof Player player) applyCooldown(player, remainingCooldown(player));
        }
    }

    private boolean managed() {
        return takeCooldownTicks >= 0 || !obfuscationMaterials.isEmpty();
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private int remainingCooldown(Player player) {
        Integer deadline = cooldowns.get(player.getUniqueId());
        if (deadline == null) return 0;
        int remaining = deadline - Bukkit.getCurrentTick();
        if (remaining <= 0) cooldowns.remove(player.getUniqueId());
        return Math.max(0, remaining);
    }

    private void applyCooldown(Player player, int ticks) {
        if (ticks <= 0) return;
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (ItemStack item : inventory.getContents()) if (!empty(item)) materials.add(item.getType());
        for (Material material : materials) player.setCooldown(material, Math.max(ticks, player.getCooldown(material)));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory() != inventory || !managed()) return;
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        if (remainingCooldown(player) > 0) {
            event.setCancelled(true);
            return;
        }
        // Collect-to-cursor can reach the top inventory even from a bottom slot.
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR ||
                (event.getClickedInventory() != inventory && event.isShiftClick())) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedInventory() != inventory) return;
        event.setCancelled(true);
        ItemStack displayed = event.getCurrentItem();
        if (empty(displayed)) return;
        String id = displayed.hasItemMeta() ? displayed.getItemMeta().getPersistentDataContainer()
                .get(maskKey, PersistentDataType.STRING) : null;
        ItemStack real = id == null ? displayed.clone() : hiddenItems.get(id);
        if (empty(real)) return;
        real = real.clone();
        int taken = 0;
        switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, SWAP_WITH_CURSOR -> {
                ItemStack cursor = event.getCursor();
                if (!empty(cursor) && !cursor.isSimilar(real)) return;
                int capacity = empty(cursor) ? real.getMaxStackSize() : cursor.getMaxStackSize() - cursor.getAmount();
                int requested = switch (event.getAction()) {
                    case PICKUP_HALF -> id == null ? (real.getAmount() + 1) / 2 : real.getAmount();
                    case PICKUP_ONE -> 1;
                    default -> real.getAmount();
                };
                taken = Math.min(capacity, requested);
                if (taken <= 0) return;
                ItemStack result = real.clone();
                result.setAmount(taken + (empty(cursor) ? 0 : cursor.getAmount()));
                event.getView().setCursor(result);
            }
            case MOVE_TO_OTHER_INVENTORY -> {
                var leftovers = player.getInventory().addItem(real.clone());
                taken = real.getAmount() - leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
            }
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                int button = event.getHotbarButton();
                ItemStack target = button == -1 ? player.getInventory().getItemInOffHand() : player.getInventory().getItem(button);
                if (!empty(target)) return;
                if (button == -1) player.getInventory().setItemInOffHand(real.clone());
                else player.getInventory().setItem(button, real.clone());
                taken = real.getAmount();
            }
            // Deposits, dropping masks and creative cloning are intentionally unavailable.
            default -> { return; }
        }
        if (taken <= 0) return;
        int remaining = real.getAmount() - taken;
        if (remaining == 0) {
            inventory.setItem(event.getSlot(), null);
            if (id != null) hiddenItems.remove(id);
        } else {
            real.setAmount(remaining);
            if (id != null) hiddenItems.put(id, real);
            else inventory.setItem(event.getSlot(), real);
        }
        if (takeCooldownTicks > 0) {
            cooldowns.put(player.getUniqueId(), Bukkit.getCurrentTick() + takeCooldownTicks);
            applyCooldown(player, takeCooldownTicks);
            player.setCooldown(real.getType(), Math.max(takeCooldownTicks, player.getCooldown(real.getType())));
            player.setCooldown(displayed.getType(), Math.max(takeCooldownTicks, player.getCooldown(displayed.getType())));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory() != inventory || !managed()) return;
        if ((event.getWhoClicked() instanceof Player player && remainingCooldown(player) > 0) ||
                event.getRawSlots().stream().anyMatch(slot -> slot < inventory.getSize())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory() == inventory) {
            viewers.add(event.getPlayer().getUniqueId());
            if (event.getPlayer() instanceof Player player) applyCooldown(player, remainingCooldown(player));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory() == inventory) {
            viewers.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(PlayerMoveEvent event) {
        if (event.getFrom().getBlock().equals(event.getTo().getBlock())) return;
        Player pl = event.getPlayer();
        if (!viewers.contains(pl.getUniqueId())) return;
        if (event.getTo().getWorld() != center.getWorld()) {
            pl.closeInventory();
            return;
        }
        if (event.getTo().distanceSquared(center) > DISTANCE_LIMIT_SQ) {
            pl.closeInventory();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void on(PlayerTeleportEvent event) {
        Player pl = event.getPlayer();
        if (!viewers.contains(pl.getUniqueId())) return;
        if (event.getTo().getWorld() != center.getWorld()) {
            pl.closeInventory();
            return;
        }
        if (event.getTo().distanceSquared(center) > DISTANCE_LIMIT_SQ) {
            pl.closeInventory();
            return;
        }
    }

    public void close() {
        closed = true;
        if (fillTask != null) {
            fillTask.cancel();
            fillTask = null;
        }
        HandlerList.unregisterAll(this);
        List.copyOf(inventory.getViewers()).forEach(HumanEntity::closeInventory);
        inventory.clear();
        hiddenItems.clear();
        cooldowns.clear();
        viewers.clear();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
