package dev.by1337.baf.loot;

import dev.by1337.baf.loot.item.ItemHolder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LootEntryTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private ItemHolder item() { return new ItemHolder(new ItemStack(Material.DIAMOND, 12)); }

    @Test void generationUsesInclusiveRangeAndLeavesOriginalStackUntouched() {
        ItemHolder item = item();
        LootTable table = new LootTable(List.of(new LootEntry(item, 1, 2, 4)));
        var amounts = new HashSet<Integer>();
        for (ItemStack stack : table.generate(200)) {
            assertEquals(Material.DIAMOND, stack.getType());
            assertTrue(stack.getAmount() >= 2 && stack.getAmount() <= 4);
            amounts.add(stack.getAmount());
        }
        assertEquals(java.util.Set.of(2, 3, 4), amounts);
        assertEquals(12, item.getItemStack().getAmount());
    }

    @Test void generationCountIsSuppliedPerCallWithoutChangingTheTable() {
        LootTable table = new LootTable(List.of(new LootEntry(item(), 1)));
        assertEquals(90, table.generate(90).size());
        assertEquals(3, table.generate(3).size());
        assertTrue(table.generate(0).isEmpty());
        assertEquals(1, table.items().size());
    }

    @Test void disabledRangePreservesStackAndEqualBoundsGiveFixedCount() {
        for (int[] range : List.of(new int[] {0, 0}, new int[] {0, 5}, new int[] {5, 0})) {
            assertEquals(12, new LootEntry(item(), 1, range[0], range[1]).generate().getAmount());
        }
        assertEquals(7, new LootEntry(item(), 1, 7, 7).generate().getAmount());
        assertEquals(64,
                new LootEntry(item(), 1, Integer.MAX_VALUE, Integer.MAX_VALUE).generate().getAmount());
    }

    @Test void rejectsInvalidRangesAndResolvesEntryPlaceholders() {
        assertThrows(IllegalArgumentException.class, () -> new LootEntry(item(), 1, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> new LootEntry(item(), 1, 1, -2));
        assertThrows(IllegalArgumentException.class, () -> new LootEntry(item(), 1, 4, 2));
        LootEntry entry = new LootEntry(item(), 2.5, 2, 4);
        assertEquals("2.5:2:4:true", entry.placeholders().setPlaceholders(
                "{weight}:{min_count}:{max_count}:{random_count}", null));
    }
}
