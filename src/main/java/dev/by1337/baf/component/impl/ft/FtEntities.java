package dev.by1337.baf.component.impl.ft;

import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

final class FtEntities implements Listener, AutoCloseable {
    interface Events {
        void landed(Entity entity);

        void died(LivingEntity entity, Player killer);

        void exploded(Entity entity);

        default void chunkUnloading(org.bukkit.Chunk chunk) {
        }
    }

    private record Owned(Entity entity, int deadline, boolean damage) {}

    private final Map<UUID, Owned> entities = new HashMap<>();
    private final Events events;
    private final int limit;
    private boolean closed;
    private int tick;

    FtEntities(Plugin plugin, int limit, Events events) {
        this.limit = limit;
        this.events = events;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    static boolean loaded(Location at) {
        return at.getWorld() != null && at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4);
    }

    <T extends Entity> T spawn(Location at, Class<T> type, int lifetime, boolean damage, Consumer<T> configure) {
        if (closed || entities.size() >= limit || !loaded(at)) return null;
        // Consumer runs before the entity is added to the world, including its first save.
        T entity = at.getWorld().spawn(at, type, value -> {
            value.setPersistent(false);
            configure.accept(value);
        });
        // A spawn listener can finish the event synchronously during World.spawn.
        if (closed || !entity.isValid()) {
            entity.remove();
            return null;
        }
        entities.put(entity.getUniqueId(), new Owned(entity, tick + lifetime, damage));
        return entity;
    }

    void tick() {
        tick++;
        for (Owned owned : List.copyOf(entities.values())) {
            Entity entity = owned.entity;
            if (!entity.isValid() || entity.isDead() || tick >= owned.deadline || !loaded(entity.getLocation()))
                remove(entity);
        }
    }

    boolean owns(Entity entity) {
        return entities.containsKey(entity.getUniqueId());
    }

    int size() {
        return entities.size();
    }

    List<LivingEntity> living() {
        return entities.values().stream().map(Owned::entity)
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).toList();
    }

    void remove(Entity entity) {
        if (entities.remove(entity.getUniqueId()) != null) entity.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void land(EntityChangeBlockEvent event) {
        if (!owns(event.getEntity())) return;
        // setDropItem(false) alone does not prevent a FallingBlock from placing a block.
        event.setCancelled(true);
        Entity entity = event.getEntity();
        if (!(entity instanceof FallingBlock)) return;
        remove(entity);
        if (!closed) events.landed(entity);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void explode(EntityExplodeEvent event) {
        if (!owns(event.getEntity())) return;
        event.blockList().clear();
        if (!closed && !event.isCancelled()) events.exploded(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)
            source = shooter;
        Owned owned = entities.get(source.getUniqueId());
        if (owned != null && !owned.damage) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) {
        if (entities.remove(event.getEntity().getUniqueId()) == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (!closed) events.died(event.getEntity(), event.getEntity().getKiller());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void projectile(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Entity shooter)) return;
        Owned parent = entities.get(shooter.getUniqueId());
        if (parent == null) return;
        if (closed || entities.size() >= limit) {
            event.setCancelled(true);
            event.getEntity().remove();
            return;
        }
        event.getEntity().setPersistent(false);
        entities.put(event.getEntity().getUniqueId(), new Owned(event.getEntity(), parent.deadline, parent.damage));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void teleport(EntityTeleportEvent event) {
        if (owns(event.getEntity()) && (event.getTo() == null || !loaded(event.getTo()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void transform(EntityTransformEvent event) {
        if (owns(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void split(SlimeSplitEvent event) {
        if (owns(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breed(EntityBreedEvent event) {
        if (owns(event.getMother()) || owns(event.getFather())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spell(EntitySpellCastEvent event) {
        if (owns(event.getEntity()) && event.getSpell() == Spellcaster.Spell.SUMMON_VEX) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void merge(ItemMergeEvent event) {
        if (owns(event.getEntity()) || owns(event.getTarget())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unloadChunk(ChunkUnloadEvent event) {
        events.chunkUnloading(event.getChunk());
        for (Owned owned : List.copyOf(entities.values())) {
            Location at = owned.entity.getLocation();
            if (at.getWorld() == event.getWorld() && (at.getBlockX() >> 4) == event.getChunk().getX()
                    && (at.getBlockZ() >> 4) == event.getChunk().getZ()) remove(owned.entity);
        }
    }

    @EventHandler
    public void unload(EntitiesUnloadEvent event) {
        // Do not resolve UUIDs with Bukkit.getEntity: it cannot find unloaded entities.
        for (Entity entity : event.getEntities()) remove(entity);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void unloadWorld(WorldUnloadEvent event) {
        for (var chunk : event.getWorld().getLoadedChunks()) events.chunkUnloading(chunk);
        for (Owned owned : List.copyOf(entities.values())) {
            if (owned.entity.getWorld() == event.getWorld()) remove(owned.entity);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        HandlerList.unregisterAll(this);
        var owned = List.copyOf(entities.values());
        entities.clear();
        for (Owned value : owned) {
            try {
                value.entity.remove();
            } catch (RuntimeException error) {
                org.slf4j.LoggerFactory.getLogger(FtEntities.class)
                        .error("Failed to remove FT entity {}", value.entity.getUniqueId(), error);
            }
        }
    }
}
