package dev.by1337.baf.component.impl.ft;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FtEntitiesTest {
    private FtEntities owner;
    private FtEntities.Events events;
    private World world;
    private Zombie entity;
    private Location at;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        world = mock(World.class);
        events = mock(FtEntities.Events.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        at = new Location(world, -1, 64, 31);
        entity = mock(Zombie.class);
        when(entity.getLocation()).thenReturn(at);
        when(entity.getWorld()).thenReturn(world);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        when(entity.isValid()).thenReturn(true);
        owner = new FtEntities(MockBukkit.createMockPlugin(), 4, events);
        when(world.spawn(eq(at), eq(Zombie.class), org.mockito.ArgumentMatchers.<Consumer<Zombie>>any())).thenAnswer(call -> {
            Consumer<Zombie> configure = call.getArgument(2);
            configure.accept(entity);
            verify(entity).setPersistent(false);
            return entity;
        });
    }

    @AfterEach
    void cleanup() {
        owner.close();
        MockBukkit.unmock();
    }

    private Zombie spawn() {
        return owner.spawn(at, Zombie.class, 100, false, e -> {});
    }

    @Test
    void neverLoadsAnUnloadedChunkOrCreatesAnEntityThere() {
        when(world.isChunkLoaded(-1, 1)).thenReturn(false);
        assertNull(spawn());
        assertEquals(0, owner.size());
        verify(world, never()).spawn(any(Location.class), eq(Zombie.class), org.mockito.ArgumentMatchers.<Consumer<Zombie>>any());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void unloadBeforeSavingRemovesByReferenceWithoutDeathRewards() {
        assertSame(entity, spawn());
        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(-1);
        when(chunk.getZ()).thenReturn(1);
        ChunkUnloadEvent event = mock(ChunkUnloadEvent.class);
        when(event.getWorld()).thenReturn(world);
        when(event.getChunk()).thenReturn(chunk);
        owner.unloadChunk(event);
        verify(entity).remove();
        assertEquals(0, owner.size());
        verify(events).chunkUnloading(chunk);
        verify(events, never()).died(any(), any());
        EntitiesUnloadEvent late = mock(EntitiesUnloadEvent.class);
        when(late.getEntities()).thenReturn(List.of(entity));
        owner.unload(late);
        verify(entity, times(1)).remove();
    }

    @Test
    void cleanupIsIdempotentAndLifetimeDoesNotCountAsDeath() {
        owner.spawn(at, Zombie.class, 2, false, e -> {});
        owner.tick();
        verify(entity, never()).remove();
        owner.tick();
        verify(entity).remove();
        verifyNoInteractions(events);
        owner.close();
        owner.close();
        verify(entity, times(1)).remove();
    }

    @Test
    void closingDuringSpawnCannotLeakTheNewEntity() {
        when(world.spawn(eq(at), eq(Zombie.class), org.mockito.ArgumentMatchers.<Consumer<Zombie>>any())).thenAnswer(call -> {
            Consumer<Zombie> configure = call.getArgument(2);
            configure.accept(entity);
            owner.close();
            return entity;
        });
        assertNull(spawn());
        assertEquals(0, owner.size());
        verify(entity).remove();
    }

    @Test
    void realDeathIsHandledOnceAndCleanupDoesNotRepeatRewards() {
        spawn();
        Player killer = mock(Player.class);
        when(entity.getKiller()).thenReturn(killer);
        EntityDeathEvent event = mock(EntityDeathEvent.class);
        when(event.getEntity()).thenReturn(entity);
        var drops = new ArrayList<org.bukkit.inventory.ItemStack>();
        drops.add(new org.bukkit.inventory.ItemStack(Material.DIAMOND));
        when(event.getDrops()).thenReturn(drops);
        owner.death(event);
        owner.death(event);
        assertTrue(drops.isEmpty());
        verify(event).setDroppedExp(0);
        verify(events, times(1)).died(entity, killer);
        owner.close();
        verify(entity, never()).remove();
    }

    @Test
    void landingPreventsWorldChangesAndCallsTheHookOnlyOnce() {
        FallingBlock block = mock(FallingBlock.class);
        when(block.getUniqueId()).thenReturn(UUID.randomUUID());
        when(block.isValid()).thenReturn(true);
        when(block.getLocation()).thenReturn(at);
        when(world.spawn(eq(at), eq(FallingBlock.class), org.mockito.ArgumentMatchers.<Consumer<FallingBlock>>any())).thenReturn(block);
        owner.spawn(at, FallingBlock.class, 100, false, e -> {});
        EntityChangeBlockEvent event = mock(EntityChangeBlockEvent.class);
        when(event.getEntity()).thenReturn(block);
        owner.land(event);
        owner.land(event);
        verify(event).setCancelled(true);
        verify(block).remove();
        verify(events).landed(block);
    }

    @Test
    void explosionClearsBlocksAndDamageSwitchIsEnforced() {
        spawn();
        EntityExplodeEvent explosion = mock(EntityExplodeEvent.class);
        when(explosion.getEntity()).thenReturn(entity);
        var blocks = new ArrayList<org.bukkit.block.Block>();
        blocks.add(mock(org.bukkit.block.Block.class));
        when(explosion.blockList()).thenReturn(blocks);
        owner.explode(explosion);
        assertTrue(blocks.isEmpty());
        verify(events).exploded(entity);
        var damage = mock(EntityDamageByEntityEvent.class);
        when(damage.getDamager()).thenReturn(entity);
        owner.damage(damage);
        verify(damage).setCancelled(true);
    }

    @Test
    void projectilesOfOwnedMobsAreAlsoNonPersistentAndRemoved() {
        spawn();
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(entity);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        var launch = mock(ProjectileLaunchEvent.class);
        when(launch.getEntity()).thenReturn(arrow);
        owner.projectile(launch);
        verify(arrow).setPersistent(false);
        assertEquals(2, owner.size());
        owner.close();
        verify(entity).remove();
        verify(arrow).remove();
    }

    @Test
    void mobBlockChangesCannotBeMistakenForFallingBlockLandings() {
        spawn();
        var change = mock(EntityChangeBlockEvent.class);
        when(change.getEntity()).thenReturn(entity);
        owner.land(change);
        verify(change).setCancelled(true);
        verify(entity, never()).remove();
        verify(events, never()).landed(any());
    }

    @Test
    void transformationAndUnloadedTeleportAreBlockedForOwnedMobsOnly() {
        spawn();
        EntityTransformEvent transformation = mock(EntityTransformEvent.class);
        when(transformation.getEntity()).thenReturn(entity);
        owner.transform(transformation);
        verify(transformation).setCancelled(true);
        EntityTeleportEvent teleport = mock(EntityTeleportEvent.class);
        when(teleport.getEntity()).thenReturn(entity);
        when(teleport.getTo()).thenReturn(new Location(world, 64, 64, 64));
        when(world.isChunkLoaded(4, 4)).thenReturn(false);
        owner.teleport(teleport);
        verify(teleport).setCancelled(true);
    }
}
