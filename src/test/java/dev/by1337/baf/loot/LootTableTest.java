package dev.by1337.baf.loot;

import dev.by1337.baf.loot.item.ItemHolder;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LootTableTest {
    private static LootEntry entry(double weight) {
        return new LootEntry(new ItemHolder(new byte[]{1}), weight);
    }

    @Test
    void ignoresZeroWeightsAndInvalidatesSelectorAfterEditing() {
        LootEntry disabled = entry(0);
        LootEntry enabled = entry(3);
        LootTable table = new LootTable(List.of(disabled, enabled));
        for (int i = 0; i < 100; i++) assertSame(enabled, table.selectItem());
        LootEntry replacement = entry(1);
        table.setItems(List.of(replacement));
        assertSame(replacement, table.selectItem());
        table.setItems(List.of(disabled));
        assertNull(table.selectItem());
        assertTrue(table.generate(5).isEmpty());
    }

    @Test
    void rejectsNegativeAndNonFiniteWeights() {
        for (double weight : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> entry(weight));
        }
        assertDoesNotThrow(() -> entry(0));
    }

    @Test
    void validatesRollsAndCopiesInputList() {
        List<LootEntry> source = new ArrayList<>(List.of(entry(1)));
        LootTable table = new LootTable(source);
        source.clear();
        assertEquals(1, table.items().size());
        assertThrows(UnsupportedOperationException.class, () -> table.items().clear());
        assertTrue(table.generate(0).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> table.generate(-1));
    }

    @Test
    void supportsWeightsWhoseSumWouldOverflow() {
        LootEntry a = entry(Double.MAX_VALUE);
        LootEntry b = entry(Double.MAX_VALUE);
        LootTable table = new LootTable(List.of(a, b));
        for (int i = 0; i < 100; i++) assertNotNull(table.selectItem());
    }
}
