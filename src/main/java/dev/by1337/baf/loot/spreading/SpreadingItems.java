package dev.by1337.baf.loot.spreading;

import dev.by1337.particle.ParticleRender;
import dev.by1337.particle.flow.emit.EmitterGraph;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class SpreadingItems implements Listener, AutoCloseable {
    private final Location origin;
    private final Queue<ItemStack> pending = new ArrayDeque<>();
    private final Map<UUID, Drop> drops = new HashMap<>();
    private final List<Material> masks;
    private final Component maskName;
    private final int interval, pickupDelay, lifetime, trailInterval;
    private final double horizontalSpeed, verticalSpeed;
    private final EmitterGraph trail;
    private int tick;
    private boolean closed;

    public SpreadingItems(Plugin plugin, Location origin, List<ItemStack> loot, int interval,
                          double horizontalSpeed, double verticalSpeed, int pickupDelay, int lifetime,
                          List<Material> masks, Component maskName, EmitterGraph trail, int trailInterval) {
        if (interval < 1 || pickupDelay < 0 || lifetime < 1 || trailInterval < 1)
            throw new IllegalArgumentException("Invalid spreading timing");
        if (!Double.isFinite(horizontalSpeed) || horizontalSpeed < 0 ||
                !Double.isFinite(verticalSpeed) || verticalSpeed < 0)
            throw new IllegalArgumentException("Invalid spreading velocity");
        if (masks.stream().anyMatch(m -> !m.isItem() || m.isAir()))
            throw new IllegalArgumentException("Obfuscation materials must be non-air items");
        this.origin = origin.clone();
        for (ItemStack item : loot) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) pending.add(item.clone());
        }
        this.interval = interval;
        this.horizontalSpeed = horizontalSpeed;
        this.verticalSpeed = verticalSpeed;
        this.pickupDelay = pickupDelay;
        this.lifetime = lifetime;
        this.masks = List.copyOf(masks);
        this.maskName = maskName;
        this.trail = trail;
        this.trailInterval = trailInterval;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void tick() {
        if (closed) return;
        if (!pending.isEmpty() && tick % interval == 0) emit();
        for (Drop drop : List.copyOf(drops.values())) {
            if (!drop.entity.isValid() || tick - drop.born >= lifetime) {
                drop.entity.remove();
                drops.remove(drop.entity.getUniqueId());
                continue;
            }
            if (trail != null && !drop.entity.isOnGround() && (tick - drop.born) % trailInterval == 0) {
                Location at = drop.entity.getLocation();
                var particles = trail.make(tick - drop.born);
                for (Player player : at.getWorld().getPlayers()) {
                    if (player.getLocation().distanceSquared(at) <= 30 * 30)
                        ParticleRender.render(player, particles, at.getX(), at.getY(), at.getZ());
                }
            }
        }
        tick++;
    }

    private void emit() {
        ItemStack original = pending.remove();
        ItemStack visible = original.clone();
        if (!masks.isEmpty()) {
            visible = new ItemStack(masks.get(ThreadLocalRandom.current().nextInt(masks.size())));
            if (maskName != null) {
                var meta = visible.getItemMeta();
                meta.displayName(maskName);
                visible.setItemMeta(meta);
            }
        }
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        Vector velocity = new Vector(Math.cos(angle) * horizontalSpeed, verticalSpeed, Math.sin(angle) * horizontalSpeed);
        Item entity = origin.getWorld().dropItem(origin, visible, item -> {
            item.setPersistent(false);
            item.setPickupDelay(pickupDelay);
            item.setVelocity(velocity);
        });
        drops.put(entity.getUniqueId(), new Drop(entity, original, tick));
    }

    public boolean isSpreadComplete() { return pending.isEmpty(); }
    public boolean isFinished() { return pending.isEmpty() && drops.isEmpty(); }
    public boolean isClosed() { return closed; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) {
        Drop drop = drops.get(event.getItem().getUniqueId());
        if (drop == null || masks.isEmpty()) return;
        event.setCancelled(true);
        if (!(event.getEntity() instanceof Player player)) return;
        var leftovers = player.getInventory().addItem(drop.original.clone());
        int remaining = leftovers.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (remaining == 0) {
            drops.remove(event.getItem().getUniqueId());
            drop.entity.remove();
        } else {
            drop.original.setAmount(remaining);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hopperPickup(InventoryPickupItemEvent event) {
        if (!masks.isEmpty() && drops.containsKey(event.getItem().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void merge(ItemMergeEvent event) {
        if (drops.containsKey(event.getEntity().getUniqueId()) || drops.containsKey(event.getTarget().getUniqueId()))
            event.setCancelled(true);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        HandlerList.unregisterAll(this);
        pending.clear();
        drops.values().forEach(drop -> drop.entity.remove());
        drops.clear();
    }

    private record Drop(Item entity, ItemStack original, int born) { }
}
