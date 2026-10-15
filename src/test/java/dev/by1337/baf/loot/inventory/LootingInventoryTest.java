package dev.by1337.baf.loot.inventory;

import dev.by1337.baf.component.impl.loot.LooterComponent;
import dev.by1337.yaml.YamlValue;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        for (int i = 0; i < loot.getInventory().getSize(); i++) if (loot.getInventory().getItem(i) != null) return i;
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
        for (int i = 0; i < player.getInventory().getSize(); i++)
            player.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
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

    private void createCooling(int delay, int maxDelay) {
        loot = new LootingInventory(9, Component.text("Cooling"), player.getLocation(), MockBukkit.createMockPlugin(),
                -1, List.of(Material.BLAZE_POWDER), Component.text("Warm"), delay, maxDelay);
        player.openInventory(loot.getInventory());
    }

    @Test
    void slotsCoolIndependentlyAndCannotBeTakenBeforeReveal() {
        createCooling(5, 5);
        assertEquals(9, loot.getInventory().getSize());
        loot.fill(List.of(new ItemStack(Material.DIAMOND, 13), new ItemStack(Material.GOLD_INGOT, 2)), 0, 3, 3);
        server.getScheduler().performTicks(2);
        assertEquals(0, occupiedCount());
        server.getScheduler().performTicks(1);
        int first = occupiedSlot();
        assertEquals(Material.BLAZE_POWDER, loot.getInventory().getItem(first).getType());
        for (InventoryAction action : List.of(InventoryAction.PICKUP_ALL, InventoryAction.PICKUP_HALF,
                InventoryAction.MOVE_TO_OTHER_INVENTORY, InventoryAction.SWAP_WITH_CURSOR,
                InventoryAction.DROP_ALL_SLOT, InventoryAction.CLONE_STACK)) {
            assertTrue(click(first, ClickType.LEFT, action).isCancelled());
        }
        var hotbar = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
                first, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
        server.getPluginManager().callEvent(hotbar);
        assertTrue(hotbar.isCancelled());
        assertNull(player.getInventory().getItem(0));
        assertTrue(player.getItemOnCursor().getType().isAir());
        player.closeInventory();
        server.getScheduler().performTicks(3);
        assertEquals(2, occupiedCount());
        server.getScheduler().performTicks(2);
        assertEquals(new ItemStack(Material.DIAMOND, 13), loot.getInventory().getItem(first));
        assertEquals(1, java.util.Arrays.stream(loot.getInventory().getContents())
                .filter(item -> item != null && item.getType() == Material.BLAZE_POWDER).count());
        server.getScheduler().performTicks(3);
        assertTrue(java.util.Arrays.stream(loot.getInventory().getContents())
                .noneMatch(item -> item != null && item.getType() == Material.BLAZE_POWDER));
        player.openInventory(loot.getInventory());
        click(first, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        assertEquals(new ItemStack(Material.DIAMOND, 13), player.getItemOnCursor());
    }

    @Test
    void randomIntervalsAndRevealDelaysStayWithinTheirOwnBounds() {
        createCooling(4, 7);
        loot.fill(Collections.nCopies(5, new ItemStack(Material.DIAMOND)), 0, 2, 5);
        Map<Integer, Integer> appeared = new java.util.HashMap<>();
        java.util.Set<Integer> revealed = new java.util.HashSet<>();
        int previousAppearance = 0;
        for (int tick = 1; tick <= 32; tick++) {
            server.getScheduler().performOneTick();
            for (int slot = 0; slot < 9; slot++) {
                ItemStack item = loot.getInventory().getItem(slot);
                if (item == null) continue;
                if (!appeared.containsKey(slot)) {
                    assertTrue(tick - previousAppearance >= 2 && tick - previousAppearance <= 5);
                    assertEquals(Material.BLAZE_POWDER, item.getType());
                    appeared.put(slot, tick);
                    previousAppearance = tick;
                }
                if (item.getType() == Material.DIAMOND && revealed.add(slot)) {
                    int delay = tick - appeared.get(slot);
                    assertTrue(delay >= 4 && delay <= 7);
                }
            }
        }
        assertEquals(5, appeared.size());
        assertEquals(5, revealed.size());
    }

    @Test
    void fullInventorySkipsAttemptsAndCanAcceptLaterItems() {
        createCooling(1, 1);
        loot.fill(Collections.nCopies(11, new ItemStack(Material.DIAMOND)), 0, 2, 2);
        server.getScheduler().performTicks(20);
        assertEquals(9, occupiedCount());
        loot.getInventory().clear();
        server.getScheduler().performTicks(3);
        assertEquals(1, occupiedCount());
        assertEquals(Material.DIAMOND, loot.getInventory().getItem(occupiedSlot()).getType());
    }

    @Test
    void revealDoesNotOverwriteReplacedSlotsAndRemovalCancelsAllTimers() {
        createCooling(5, 5);
        loot.fill(List.of(new ItemStack(Material.DIAMOND), new ItemStack(Material.GOLD_INGOT)), 0, 3, 3);
        server.getScheduler().performTicks(3);
        int slot = occupiedSlot();
        loot.getInventory().setItem(slot, new ItemStack(Material.EMERALD));
        server.getScheduler().performTicks(5);
        assertEquals(Material.EMERALD, loot.getInventory().getItem(slot).getType());
        loot.fill(Collections.nCopies(3, new ItemStack(Material.STONE)), 0, 3, 3);
        loot.close();
        server.getScheduler().performTicks(30);
        assertEquals(0, occupiedCount());
        assertNotSame(loot.getInventory(), player.getOpenInventory().getTopInventory());
    }

    private long occupiedCount() {
        return java.util.Arrays.stream(loot.getInventory().getContents()).filter(item -> item != null).count();
    }

    @Test
    void migratedAirdropLooterConfigDecodes() {
        var node = dev.by1337.yaml.YamlMap.load(new java.io.File("CONFIGS/workflow/airdrop.yml")).get("nodes.looter");
        var decoded = LooterComponent.Config.DECODER.decode(node);
        assertFalse(decoded.hasError(), decoded.error());
    }

    @Test
    void typedConfigurationRejectsInvalidDurationsAndMaskMaterials() {
        var defaults = LooterComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("loot_table", "test")));
        assertFalse(defaults.hasError(), defaults.error());
        for (var invalid : List.of(Map.of("rolls", -1), Map.of("take_cooldown_ticks", -2), Map.of("fill_duration_ticks", -1),
                Map.of("size", 0), Map.of("size", 10), Map.of("size", 63),
                Map.of("fill_interval_ticks", -1), Map.of("reveal_delay_ticks", -1),
                Map.of("fill_interval_max_ticks", -1), Map.of("reveal_delay_max_ticks", -1),
                Map.of("fill_interval_ticks", 5, "fill_interval_max_ticks", 4),
                Map.of("reveal_delay_ticks", 5, "reveal_delay_max_ticks", 4),
                Map.of("fill_interval_max_ticks", 5), Map.of("reveal_delay_max_ticks", 5),
                Map.of("fill_duration_ticks", 20, "fill_interval_ticks", 5),
                Map.of("obfuscation_materials", List.of("air")), Map.of("obfuscation_materials", List.of("not_a_material")))) {
            var config = new java.util.HashMap<String, Object>(invalid);
            config.put("loot_table", "test");
            assertTrue(LooterComponent.Config.DECODER.decode(YamlValue.wrap(config)).hasError(), config.toString());
        }
    }
}
