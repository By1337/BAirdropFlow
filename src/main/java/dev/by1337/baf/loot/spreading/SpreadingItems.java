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
    private final java.util.function.Consumer<Item> configure;
    private final EmitterGraph trail;
    private int tick;
    private int spreadDuration;
    private final int total;
    private int trailLifetime = Integer.MAX_VALUE;
    private boolean closed;

    public SpreadingItems(Plugin plugin, Location origin, List<ItemStack> loot, int interval,
                          double horizontalSpeed, double verticalSpeed, int pickupDelay, int lifetime,
                          List<Material> masks, Component maskName, EmitterGraph trail, int trailInterval) {
        this(plugin, origin, loot, interval, horizontalSpeed, verticalSpeed, pickupDelay, lifetime, masks, maskName, trail, trailInterval, item -> {});
    }

    public SpreadingItems(Plugin plugin, Location origin, List<ItemStack> loot, int interval,
                          double horizontalSpeed, double verticalSpeed, int pickupDelay, int lifetime,
                          List<Material> masks, Component maskName, EmitterGraph trail, int trailInterval,
                          java.util.function.Consumer<Item> configure) {
        this.configure = java.util.Objects.requireNonNull(configure);
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
        this.total = pending.size();
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

    /**
     * Distribute all stacks over exactly this many tick calls; zero preserves interval mode.
     */
    public SpreadingItems spreadOver(int ticks) {
        if (ticks < 0 || tick != 0) throw new IllegalArgumentException("Set spread duration before ticking");
        spreadDuration = ticks;
        return this;
    }

    public SpreadingItems limitTrailLifetime(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Invalid trail lifetime");
        trailLifetime = ticks;
        return this;
    }

    public void tick() {
        if (closed) return;
        if (spreadDuration > 0) {
            int due = (int) ((long) Math.min((long) tick + 1, spreadDuration) * total / spreadDuration);
            while (!pending.isEmpty() && total - pending.size() < due) emit();
        } else if (!pending.isEmpty() && tick % interval == 0) emit();
        for (Drop drop : List.copyOf(drops.values())) {
            if (!drop.entity.isValid() || tick - drop.born >= lifetime) {
                drop.entity.remove();
                drops.remove(drop.entity.getUniqueId());
                continue;
            }
            if (trail != null && tick - drop.born < trailLifetime && !drop.entity.isOnGround() && (tick - drop.born) % trailInterval == 0) {
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
        if (!origin.getWorld().isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) return;
        Item entity = origin.getWorld().dropItem(origin, visible, item -> {
            item.setPersistent(false);
            item.setPickupDelay(pickupDelay);
            item.setVelocity(velocity);
            configure.accept(item);
        });
        if (closed || !entity.isValid()) {
            entity.remove();
            return;
        }
        drops.put(entity.getUniqueId(), new Drop(entity, original, tick));
    }

    public int queuedAndLiveCount() {
        return pending.size() + drops.size();
    }

    public boolean isSpreadComplete() {
        return pending.isEmpty();
    }

    public boolean isFinished() {
        return pending.isEmpty() && drops.isEmpty();
    }

    public boolean isClosed() {
        return closed;
    }

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

    @EventHandler
    public void unload(org.bukkit.event.world.EntitiesUnloadEvent event) {
        for (var entity : event.getEntities()) {
            Drop drop = drops.remove(entity.getUniqueId());
            if (drop != null) drop.entity.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unload(org.bukkit.event.world.ChunkUnloadEvent event) {
        for (Drop drop : List.copyOf(drops.values())) {
            Location at = drop.entity.getLocation();
            if (at.getWorld() == event.getWorld() && (at.getBlockX() >> 4) == event.getChunk().getX()
                    && (at.getBlockZ() >> 4) == event.getChunk().getZ()) {
                drops.remove(drop.entity.getUniqueId());
                drop.entity.remove();
            }
        }
        if (origin.getWorld() == event.getWorld() && (origin.getBlockX() >> 4) == event.getChunk().getX()
                && (origin.getBlockZ() >> 4) == event.getChunk().getZ()) close();
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

    private record Drop(Item entity, ItemStack original, int born) {}
}
