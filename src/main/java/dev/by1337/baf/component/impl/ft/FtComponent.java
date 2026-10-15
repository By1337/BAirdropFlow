package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.BAF;
import dev.by1337.baf.component.AbstractComponent;
import dev.by1337.baf.component.system.TimerSystem;
import dev.by1337.baf.ref.CleanerSlot;
import dev.by1337.core.util.text.minimessage.MiniMessage;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.particle.ParticleRender;
import dev.by1337.particle.flow.emit.EmitterGraph;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Consumer;

abstract class FtComponent extends AbstractComponent implements TimerSystem.Tickable, FtEntities.Events {
    protected final Airdrop airdrop;
    protected final FtSettings settings;
    protected FtEntities entities;

    private record Anchor(Location at) {}

    protected Location origin;
    protected int age;
    protected int lifetime;
    private int duration;
    private int killed;
    private Location lastLocation;
    private final CleanerSlot<FtEntities> owner;

    private record Trail(Entity entity, int born) {}

    private final Map<UUID, Trail> trails = new HashMap<>();
    private EmitterGraph trail;
    private final List<dev.by1337.baf.loot.spreading.SpreadingItems> scatters = new ArrayList<>();
    private dev.by1337.cmd.Command<EventContext> commands;
    private dev.by1337.edsl.messages.Messages messages;

    FtComponent(FtSettings settings, String name, Airdrop airdrop) {
        super(name, airdrop.components());
        this.airdrop = airdrop;
        this.settings = settings;
        owner = new CleanerSlot<>(owned -> {
            try {
                owned.close();
            } finally {
                for (var scatter : scatters) scatter.close();
                scatters.clear();
                trails.clear();
                removed();
            }
        }, airdrop.cleaner());
        exportPlaceholder("alive", () -> entities == null ? 0 : entities.size());
        exportPlaceholder("killed", () -> killed);
        exportPlaceholder("x", () -> lastLocation == null ? airdrop.x() : lastLocation.getX());
        exportPlaceholder("y", () -> lastLocation == null ? airdrop.y() : lastLocation.getY());
        exportPlaceholder("z", () -> lastLocation == null ? airdrop.z() : lastLocation.getZ());
    }

    @Override
    public final void onCreate(EventContext ctx) {
        commands = ctx.command();
        messages = ctx.messages();
        var offset = settings.offsets().getVector(airdrop);
        var anchor = ctx.get(Anchor.class);
        origin = (anchor == null ? airdrop.location() : anchor.at.clone()).add(offset.x(), offset.y(), offset.z());
        lifetime = settings.lifetime().getInt(airdrop);
        duration = settings.ticks().getInt(airdrop);
        String animation = settings.trail().getString(airdrop);
        if (!animation.isBlank()) {
            trail = BAF.particleGraphs().get(animation);
            if (trail == null) throw new IllegalArgumentException("Unknown trail_animation " + animation);
        }
        owner.set(entities = new FtEntities(airdrop.manager().plugin(), settings.maxEntities().getInt(airdrop), this));
        try {
            start(ctx);
            if (!isRemoved()) settings.create().handle(ctx);
        } catch (RuntimeException | Error error) {
            owner.cleanup();
            throw error;
        }
    }

    protected void start(EventContext ctx) {
    }

    protected abstract void tick(EventContext ctx);

    @Override
    public final void onSyncTick(EventContext ctx) {
        if (isRemoved() || owner.get() == null) return;
        try {
            runTick(ctx);
        } catch (RuntimeException | Error error) {
            try {
                if (!isRemoved()) markRemoved(ctx);
            } catch (RuntimeException | Error cleanup) {
                error.addSuppressed(cleanup);
            }
            throw error;
        }
    }

    private void runTick(EventContext ctx) {
        entities.tick();
        for (var scatter : List.copyOf(scatters)) {
            scatter.tick();
            if (scatter.isClosed() || scatter.isFinished()) {
                scatter.close();
                scatters.remove(scatter);
            }
        }
        tick(ctx);
        if (isRemoved() || owner.get() == null) return;
        if (trail != null) {
            for (Trail entry : List.copyOf(trails.values())) {
                Entity entity = entry.entity;
                if (!entities.owns(entity) || age - entry.born >= settings.trailTicks().getInt(airdrop)) {
                    trails.remove(entity.getUniqueId());
                    continue;
                }
                Location at = entity.getLocation();
                var packets = trail.make(age - entry.born);
                for (Player player : airdrop.nearbyPlayers())
                    ParticleRender.render(player, packets, at.getX(), at.getY(), at.getZ());
            }
        }
        if (++age >= duration && duration != -1) finish(ctx);
    }

    protected final void finish(EventContext ctx) {
        try {
            settings.finish().handle(ctx);
        } finally {
            if (!isRemoved()) markRemoved(ctx);
        }
    }

    @Override
    public final void onRemove(EventContext ctx) {
        owner.cleanup();
        settings.remove().handle(ctx);
    }

    protected void removed() {
    }

    protected final <T extends Entity> T spawn(Location at, Class<T> type, boolean damage, Consumer<T> configure) {
        if (isRemoved() || owner.get() == null) return null;
        if (entities.size() + scatters.stream().mapToInt(s -> s.queuedAndLiveCount()).sum() >= settings.maxEntities().getInt(airdrop))
            return null;
        T entity = entities.spawn(at, type, lifetime, damage, configure);
        if (entity != null && trail != null) trails.put(entity.getUniqueId(), new Trail(entity, age));
        return entity;
    }

    protected final void particle(Location at, org.bukkit.Particle particle, int count, double spread) {
        if (FtEntities.loaded(at)) at.getWorld().spawnParticle(particle, at, count, spread, spread, spread, 0);
    }

    protected final void radialDamage(Location at, double radius, double maxDamage) {
        if (radius <= 0 || maxDamage <= 0 || !FtEntities.loaded(at)) return;
        for (Player player : at.getNearbyPlayers(radius)) {
            double distance = player.getLocation().distance(at);
            if (distance <= radius) player.damage(Math.max(0, maxDamage * (1 - distance / radius)));
        }
    }

    protected final boolean scatter(Location at, String table, int rolls, List<org.bukkit.Material> masks,
                                    String maskName, Consumer<Item> configure) {
        if (isRemoved() || owner.get() == null) return false;
        if (table.isBlank() || rolls <= 0 || !FtEntities.loaded(at)) return false;
        var loot = airdrop.manager().lootTables().get(table);
        if (loot == null) throw new IllegalArgumentException("Unknown loot table " + table);
        if (entities.size() + scatters.stream().mapToInt(s -> s.queuedAndLiveCount()).sum() + rolls > settings.maxEntities().getInt(airdrop))
            return false;
        var spread = new dev.by1337.baf.loot.spreading.SpreadingItems(airdrop.manager().plugin(), at,
                loot.generate(rolls), 1, 0, 0, 0, lifetime, masks,
                maskName.isBlank() ? null : MiniMessage.deserialize(maskName), trail, 1, configure);
        spread.limitTrailLifetime(settings.trailTicks().getInt(airdrop));
        scatters.add(spread);
        spread.tick();
        return true;
    }

    protected final boolean dropsFinished() {
        return scatters.isEmpty();
    }

    protected final void loot(Location at, String table, int rolls, Vector velocity) {
        if (isRemoved() || owner.get() == null) return;
        if (table.isBlank() || rolls <= 0 || !FtEntities.loaded(at)) return;
        var loot = airdrop.manager().lootTables().get(table);
        if (loot == null) throw new IllegalArgumentException("Unknown loot table " + table);
        for (var stack : loot.generate(rolls)) {
            spawn(at, Item.class, false, item -> {
                item.setItemStack(stack);
                item.setVelocity(velocity.clone());
            });
        }
    }

    protected final void emit(EventHandler handler, Location at, Entity entity, Player target) {
        if (isRemoved() || owner.get() == null) return;
        lastLocation = at.clone();
        // Fresh context: the library's nested with(Location) does not shadow an earlier Location.
        try (var ctx = new EventContext(false, null, Airdrop.PLACEHOLDERS.bindCtx(airdrop)).setCommand(commands)) {
            ctx.setMessages(messages);
            ctx.with(at.clone());
            ctx.with(new Anchor(at.clone()));
            ctx.with(airdrop);
            if (entity != null) ctx.with(entity);
            if (target == null) handler.handle(ctx);
            else try (var ignored = ctx.target(target)) {
                handler.handle(ctx);
            }
        }
    }

    @Override
    public void chunkUnloading(org.bukkit.Chunk chunk) {
    }

    @Override
    public void landed(Entity entity) {
        emit(settings.land(), entity.getLocation(), entity, null);
    }

    @Override
    public void died(LivingEntity entity, Player killer) {
        killed++;
        emit(settings.death(), entity.getLocation(), entity, killer);
    }

    @Override
    public void exploded(Entity entity) {
        emit(settings.impact(), entity.getLocation(), entity, null);
    }

    static int randomInt(int min, int max) {
        return (int) java.util.concurrent.ThreadLocalRandom.current().nextLong(min, (long) max + 1);
    }

    static double random(double min, double max) {
        return min == max ? min : java.util.concurrent.ThreadLocalRandom.current().nextDouble(min, max);
    }
}
