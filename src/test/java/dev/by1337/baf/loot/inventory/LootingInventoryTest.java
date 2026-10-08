package dev.by1337.baf.loot.inventory;

import dev.by1337.baf.component.impl.loot.LooterComponent;
import dev.by1337.yaml.YamlValue;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LootingInventoryTest {
    private ServerMock server;
    private PlayerMock player;
    private LootingInventory loot;

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        player = server.addPlayer();
    }

    @AfterEach
    void cleanup() {
        if (loot != null) loot.close();
        MockBukkit.unmock();
    }

    private void create(int cooldown, boolean masked) {
        loot = new LootingInventory(54, Component.text("Loot"), player.getLocation(), MockBukkit.createMockPlugin(),
                cooldown, masked ? List.of(Material.GRAY_DYE) : List.of(), Component.text("Unknown"));
        player.openInventory(loot.getInventory());
    }

    private int occupiedSlot() {
        for (int i = 0; i < 54; i++) if (loot.getInventory().getItem(i) != null) return i;
        throw new AssertionError("No loot");
    }

    private InventoryClickEvent click(int slot, ClickType click, InventoryAction action) {
        var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, click, action);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void masksContainOnlyAnOpaqueIdAndRevealTheOriginalStack() {
        create(-1, true);
        var original = new ItemStack(Material.DIAMOND, 13);
        var meta = original.getItemMeta();
        meta.displayName(Component.text("Secret diamond"));
        original.setItemMeta(meta);
        loot.fill(List.of(original));
        int slot = occupiedSlot();
        var mask = loot.getInventory().getItem(slot);
        assertEquals(Material.GRAY_DYE, mask.getType());
        assertEquals(1, mask.getAmount());
        assertEquals(Component.text("Unknown"), mask.getItemMeta().displayName());
        var pdc = mask.getItemMeta().getPersistentDataContainer();
        assertEquals(1, pdc.getKeys().size());
        var id = pdc.get(pdc.getKeys().iterator().next(), PersistentDataType.STRING);
        assertDoesNotThrow(() -> java.util.UUID.fromString(id));
        assertTrue(click(slot, ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        assertEquals(original, player.getItemOnCursor());
        assertNull(loot.getInventory().getItem(slot));
    }

    @Test
    void cooldownBlocksClicksAndDragsUntilItExpiresEvenAfterReopening() {
        create(10, false);
        loot.fill(List.of(new ItemStack(Material.DIAMOND), new ItemStack(Material.GOLD_INGOT)));
        click(occupiedSlot(), ClickType.LEFT, InventoryAction.PICKUP_ALL);
        int second = occupiedSlot();
        Material material = loot.getInventory().getItem(second).getType();
        assertEquals(10, player.getCooldown(material));
        player.setItemOnCursor(null);
        player.closeInventory();
        player.openInventory(loot.getInventory());
        assertTrue(click(second, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        assertNotNull(loot.getInventory().getItem(second));
        var drag = new InventoryDragEvent(player.getOpenInventory(), new ItemStack(Material.STONE),
                new ItemStack(Material.STONE, 2), false, Map.of(54, new ItemStack(Material.STONE)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
        server.getScheduler().performTicks(10);
        click(second, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertEquals(material, player.getItemOnCursor().getType());
        assertNull(loot.getInventory().getItem(second));
    }

    @Test
    void partialShiftTransferKeepsTheRemainderHiddenAndFullInventoryDoesNotStartCooldown() {
        create(10, true);
        // MockBukkit addItem also uses armor/offhand slots, so fill those for this test.
        for (int i = 0; i < player.getInventory().getSize(); i++) player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 60));
        loot.fill(List.of(new ItemStack(Material.DIAMOND, 13)));
        int slot = occupiedSlot();
        click(slot, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertEquals(64, player.getInventory().getItem(0).getAmount());
        assertEquals(Material.GRAY_DYE, loot.getInventory().getItem(slot).getType());
        server.getScheduler().performTicks(10);
        click(slot, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        assertEquals(0, player.getCooldown(Material.GRAY_DYE));
        click(slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertEquals(new ItemStack(Material.DIAMOND, 9), player.getItemOnCursor());
    }

    @Test
    void blockedCollectionCloningAndDepositsCannotExtractMasks() {
        create(-1, true);
        loot.fill(List.of(new ItemStack(Material.DIAMOND)));
        int slot = occupiedSlot();
        assertTrue(click(54, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR).isCancelled());
        assertTrue(click(slot, ClickType.MIDDLE, InventoryAction.CLONE_STACK).isCancelled());
        assertTrue(click(54, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY).isCancelled());
        assertEquals(Material.GRAY_DYE, loot.getInventory().getItem(slot).getType());
        var hotbar = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                slot, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
        server.getPluginManager().callEvent(hotbar);
        assertEquals(Material.DIAMOND, player.getInventory().getItem(0).getType());
        assertNull(loot.getInventory().getItem(slot));
    }

    @Test
    void gradualFillDiscardsFullSlotsButContinuesWhenPlayersFreeSpace() {
        create(-1, false);
        loot.fill(Collections.nCopies(90, new ItemStack(Material.DIAMOND)), 200);
        assertTrue(java.util.Arrays.stream(loot.getInventory().getContents()).allMatch(i -> i == null));
        server.getScheduler().performTicks(100);
        assertEquals(45, java.util.Arrays.stream(loot.getInventory().getContents()).filter(i -> i != null).count());
        server.getScheduler().performTicks(50);
        assertEquals(-1, loot.getInventory().firstEmpty());
        loot.getInventory().clear();
        server.getScheduler().performTicks(50);
        assertEquals(23, java.util.Arrays.stream(loot.getInventory().getContents()).filter(i -> i != null).count());
    }

    @Test
    void removalCancelsFurtherInsertionsAndDefaultsKeepVanillaClicks() {
        create(-1, false);
        loot.fill(List.of(new ItemStack(Material.DIAMOND)));
        assertFalse(click(occupiedSlot(), ClickType.LEFT, InventoryAction.PICKUP_ALL).isCancelled());
        loot.getInventory().clear();
        loot.fill(List.of(new ItemStack(Material.DIAMOND)), 20);
        loot.close();
        server.getScheduler().performTicks(30);
        assertEquals(0, loot.getInventory().firstEmpty());
    }

    @Test
    void maskedLootCanMergeWithTheRealCursorWithoutLosingTheRemainder() {
        create(-1, true);
        loot.fill(List.of(new ItemStack(Material.DIAMOND, 13)));
        player.setItemOnCursor(new ItemStack(Material.DIAMOND, 60));
        int slot = occupiedSlot();
        click(slot, ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
        assertEquals(new ItemStack(Material.DIAMOND, 64), player.getItemOnCursor());
        assertEquals(Material.GRAY_DYE, loot.getInventory().getItem(slot).getType());
        player.setItemOnCursor(null);
        click(slot, ClickType.RIGHT, InventoryAction.PICKUP_HALF);
        assertEquals(new ItemStack(Material.DIAMOND, 9), player.getItemOnCursor());
        assertNull(loot.getInventory().getItem(slot));
    }

    @Test
    void cooldownIsIndividualAndAppliesToNewlyArrivingMaterials() {
        create(20, false);
        loot.fill(List.of(new ItemStack(Material.DIAMOND)));
        click(occupiedSlot(), ClickType.LEFT, InventoryAction.PICKUP_ALL);
        loot.fill(List.of(new ItemStack(Material.GOLD_INGOT)), 4);
        server.getScheduler().performTicks(4);
        assertEquals(16, player.getCooldown(Material.GOLD_INGOT));
        var other = server.addPlayer();
        other.openInventory(loot.getInventory());
        var click = new InventoryClickEvent(other.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                occupiedSlot(), ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(click);
        assertEquals(Material.GOLD_INGOT, other.getItemOnCursor().getType());
    }

    @Test
    void cancelledClicksLeaveTheLootAndCooldownUntouched() {
        create(20, true);
        loot.fill(List.of(new ItemStack(Material.DIAMOND)));
        var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                occupiedSlot(), ClickType.LEFT, InventoryAction.PICKUP_ALL);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertEquals(Material.GRAY_DYE, loot.getInventory().getItem(occupiedSlot()).getType());
        assertEquals(0, player.getCooldown(Material.GRAY_DYE));
        assertTrue(player.getItemOnCursor().getType().isAir());
    }

    @Test
    void typedConfigurationRejectsInvalidDurationsAndMaskMaterials() {
        var defaults = LooterComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("loot_table", "test")));
        assertFalse(defaults.hasError(), defaults.error());
        for (var invalid : List.of(Map.of("rolls", -1), Map.of("take_cooldown_ticks", -2), Map.of("fill_duration_ticks", -1),
                Map.of("obfuscation_materials", List.of("air")), Map.of("obfuscation_materials", List.of("not_a_material")))) {
            var config = new java.util.HashMap<String, Object>(invalid);
            config.put("loot_table", "test");
            assertTrue(LooterComponent.Config.DECODER.decode(YamlValue.wrap(config)).hasError(), config.toString());
        }
    }
}
