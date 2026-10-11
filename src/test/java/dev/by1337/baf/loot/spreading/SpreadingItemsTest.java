package dev.by1337.baf.loot.spreading;

import dev.by1337.particle.flow.emit.EmitterGraph;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpreadingItemsTest {
    private ServerMock server;
    private PlayerMock player;
    private World world;
    private SpreadingItems spreading;
    private final List<Item> entities = new ArrayList<>();
    private final List<ItemStack> visible = new ArrayList<>();

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        world = mock(World.class);
        when(world.dropItem(any(Location.class), any(ItemStack.class), any())).thenAnswer(call -> {
            Item entity = mock(Item.class);
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            when(entity.isValid()).thenReturn(true);
            when(entity.getLocation()).thenReturn(new Location(world, 0, 65, 0));
            doAnswer(ignored -> { when(entity.isValid()).thenReturn(false); return null; }).when(entity).remove();
            entities.add(entity);
            visible.add(((ItemStack) call.getArgument(1)).clone());
            Consumer<Item> initializer = call.getArgument(2);
            initializer.accept(entity);
            return entity;
        });
    }

    @AfterEach
    void cleanup() {
        if (spreading != null) spreading.close();
        MockBukkit.unmock();
    }

    private void create(List<ItemStack> loot, boolean masked, int lifetime, EmitterGraph trail) {
        spreading = new SpreadingItems(MockBukkit.createMockPlugin(), new Location(world, 0, 65, 0),
                loot, 3, 0.4, 0.6, 20, lifetime, masked ? List.of(Material.GRAY_DYE) : List.of(),
                Component.text("Unknown"), trail, 2);
    }

    private EntityPickupItemEvent pickup() {
        var event = new EntityPickupItemEvent(player, entities.getFirst(), 0);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void emitsAtConfiguredIntervalsAndInitializesEachEntity() {
        create(List.of(new ItemStack(Material.DIAMOND), new ItemStack(Material.GOLD_INGOT)), false, 30, null);
        spreading.tick();
        assertEquals(1, entities.size());
        spreading.tick();
        spreading.tick();
        assertEquals(1, entities.size());
        spreading.tick();
        assertEquals(2, entities.size());
        assertTrue(spreading.isSpreadComplete());
        assertFalse(spreading.isFinished());
        assertEquals(Material.DIAMOND, visible.getFirst().getType());
        for (Item entity : entities) {
            verify(entity).setPersistent(false);
            verify(entity).setPickupDelay(20);
            var velocity = org.mockito.ArgumentCaptor.forClass(Vector.class);
            verify(entity).setVelocity(velocity.capture());
            Vector v = velocity.getValue();
            assertEquals(0.4, Math.hypot(v.getX(), v.getZ()), 0.00001);
            assertEquals(0.6, v.getY());
        }
    }

    @Test
    void masksHideMetadataAndGiveTheOriginalOnPickup() {
        ItemStack original = new ItemStack(Material.DIAMOND, 13);
        var meta = original.getItemMeta();
        meta.displayName(Component.text("Secret"));
        original.setItemMeta(meta);
        create(List.of(original), true, 30, null);
        spreading.tick();
        assertEquals(Material.GRAY_DYE, visible.getFirst().getType());
        assertEquals(1, visible.getFirst().getAmount());
        assertEquals(Component.text("Unknown"), visible.getFirst().getItemMeta().displayName());
        assertTrue(visible.getFirst().getItemMeta().getPersistentDataContainer().isEmpty());
        assertTrue(pickup().isCancelled());
        assertEquals(original, player.getInventory().getItem(0));
        verify(entities.getFirst()).remove();
        assertTrue(spreading.isFinished());
    }

    @Test
    void fullAndPartiallyFullInventoriesRetainTheUncollectedLoot() {
        create(List.of(new ItemStack(Material.DIAMOND, 13)), true, 30, null);
        spreading.tick();
        for (int i = 0; i < player.getInventory().getSize(); i++)
            player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        pickup();
        verify(entities.getFirst(), never()).remove();
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 60));
        pickup();
        assertEquals(64, player.getInventory().getItem(0).getAmount());
        verify(entities.getFirst(), never()).remove();
        player.getInventory().setItem(1, null);
        pickup();
        assertEquals(new ItemStack(Material.DIAMOND, 9), player.getInventory().getItem(1));
        assertTrue(spreading.isFinished());
    }

    @Test
    void cancelledPickupsDoNotGrantLootAndMasksCannotEnterHoppersOrMerge() {
        create(List.of(new ItemStack(Material.DIAMOND)), true, 30, null);
        spreading.tick();
        var cancelled = new EntityPickupItemEvent(player, entities.getFirst(), 0);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        assertTrue(player.getInventory().isEmpty());
        var hopper = new InventoryPickupItemEvent(mock(Inventory.class), entities.getFirst());
        server.getPluginManager().callEvent(hopper);
        assertTrue(hopper.isCancelled());
        var merge = new ItemMergeEvent(mock(Item.class), entities.getFirst());
        server.getPluginManager().callEvent(merge);
        assertTrue(merge.isCancelled());
    }

    @Test
    void vanillaPickupAndHoppersWorkWithoutMasks() {
        create(List.of(new ItemStack(Material.DIAMOND)), false, 30, null);
        spreading.tick();
        assertFalse(pickup().isCancelled());
        var hopper = new InventoryPickupItemEvent(mock(Inventory.class), entities.getFirst());
        server.getPluginManager().callEvent(hopper);
        assertFalse(hopper.isCancelled());
    }

    @Test
    void everyAirborneItemGetsItsOwnTrailUntilLanding() {
        var graph = mock(EmitterGraph.class);
        create(List.of(new ItemStack(Material.DIAMOND), new ItemStack(Material.GOLD_INGOT)), false, 30, graph);
        for (int i = 0; i < 6; i++) spreading.tick();
        verify(graph, times(2)).make(0);
        verify(graph, times(2)).make(2);
        verify(graph).make(4);
        entities.forEach(entity -> when(entity.isOnGround()).thenReturn(true));
        clearInvocations(graph);
        spreading.tick();
        spreading.tick();
        verifyNoInteractions(graph);
    }

    @Test
    void expiresEachDropAndCloseStopsPendingEmissions() {
        create(List.of(new ItemStack(Material.DIAMOND), new ItemStack(Material.GOLD_INGOT)), true, 2, null);
        spreading.tick();
        spreading.tick();
        verify(entities.getFirst(), never()).remove();
        spreading.tick();
        verify(entities.getFirst()).remove();
        spreading.close();
        for (int i = 0; i < 10; i++) spreading.tick();
        assertEquals(1, entities.size());
        assertTrue(spreading.isFinished());
        assertTrue(spreading.isClosed());
        assertEquals(0, org.bukkit.event.HandlerList.getRegisteredListeners(server.getPluginManager().getPlugins()[0]).size());
    }

    @Test
    void closeRemovesAllActiveEntities() {
        create(List.of(new ItemStack(Material.DIAMOND), new ItemStack(Material.GOLD_INGOT)), true, 30, null);
        for (int i = 0; i < 4; i++) spreading.tick();
        spreading.close();
        entities.forEach(entity -> verify(entity).remove());
        spreading.close();
        entities.forEach(entity -> verify(entity, times(1)).remove());
    }
}
