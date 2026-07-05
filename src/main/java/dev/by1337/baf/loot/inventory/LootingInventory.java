package dev.by1337.baf.loot.inventory;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LootingInventory implements Listener, InventoryHolder {
    private final int DISTANCE_LIMIT_SQ = 6 * 6; // 4.5 блока ванильный лимит, сделаем 6
    private final Inventory inventory;
    private final Set<UUID> viewers = new HashSet<>();
    private final Location center;

    public LootingInventory(int size, @NotNull Component title, Location center, Plugin plugin) {
        this.center = center;
        this.inventory = Bukkit.createInventory(this, size, title);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void fill(List<ItemStack> list) {
        for (ItemStack itemStack : list) {
            int slot = inventory.firstEmpty();
            if (slot == -1) return;
            inventory.setItem(slot, itemStack);
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory() == inventory) {
            viewers.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory() == inventory) {
            viewers.remove(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
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

    @EventHandler
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
        HandlerList.unregisterAll(this);
        new ArrayList<>(inventory.getViewers()).forEach(HumanEntity::closeInventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
