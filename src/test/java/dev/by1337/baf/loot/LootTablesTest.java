package dev.by1337.baf.loot;

import dev.by1337.baf.loot.item.ItemHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LootTablesTest {
    @TempDir Path dir;
    private static LootTable table() { return new LootTable(List.of(), 1); }

    @Test void replacementAndRemovalMaintainReverseLookup() {
        LootTables registry = new LootTables();
        LootTable first = table();
        LootTable second = table();
        registry.set("loot/default", first);
        assertSame(first, registry.set("loot/default", second));
        assertNull(registry.getId(first));
        assertEquals("loot/default", registry.getId(second));
        assertThrows(IllegalArgumentException.class, () -> registry.set("other", second));
        assertSame(second, registry.remove("loot/default"));
        assertNull(registry.getId(second));
        assertTrue(registry.ids().isEmpty());
    }

    @Test void onlySessionOwnerCanUnlockTable() {
        LootTables registry = new LootTables();
        LootTable table = table();
        registry.set("test", table);
        Object editor = new Object();
        registry.beginEdit(table, editor);
        assertThrows(IllegalStateException.class, () -> registry.beginEdit(table, new Object()));
        assertThrows(IllegalStateException.class, () -> registry.remove("test"));
        assertThrows(IllegalStateException.class, () -> registry.set("test", table()));
        assertThrows(IllegalStateException.class, registry::clear);
        registry.endEdit(table, new Object());
        assertThrows(IllegalStateException.class, () -> registry.remove("test"));
        registry.endEdit(table, editor);
        assertSame(table, registry.remove("test"));
    }

    @Test void roundTripsWeightsRollsAndItemBytes() throws Exception {
        Path file = dir.resolve("loot.yml");
        Files.writeString(file, "{}");
        LootTables registry = new LootTables();
        registry.load(file.toFile());
        LootEntry entry = new LootEntry(new ItemHolder(new byte[] {1, 2, 3}), 2.5, 2, 9);
        registry.set("loot/example", new LootTable(List.of(entry), 7));
        registry.save();
        LootTables reloaded = new LootTables();
        reloaded.load(file.toFile());
        LootTable table = reloaded.get("loot/example");
        assertEquals(7, table.rolls());
        assertEquals(2.5, table.items().get(0).weight());
        assertEquals(2, table.items().get(0).minCount());
        assertEquals(9, table.items().get(0).maxCount());
        assertEquals(entry.itemHolder().asBase64(), table.items().get(0).itemHolder().asBase64());
        reloaded.remove("loot/example");
        reloaded.save();
        registry.load(file.toFile());
        assertTrue(registry.ids().isEmpty());
        try (var files = Files.list(dir)) { assertTrue(files.noneMatch(v -> v.toString().endsWith(".tmp"))); }
    }

    @Test void invalidFileDoesNotReplaceLoadedTablesOrOverwriteFile() throws Exception {
        Path file = dir.resolve("loot.yml");
        Files.writeString(file, "{}");
        LootTables registry = new LootTables();
        registry.load(file.toFile());
        LootTable old = table();
        registry.set("old", old);
        String invalid = "broken:\n  rolls: -1\n  items: []\n";
        Files.writeString(file, invalid);
        assertThrows(IllegalStateException.class, () -> registry.load(file.toFile()));
        assertSame(old, registry.get("old"));
        assertEquals(invalid, Files.readString(file));
        assertEquals(invalid, Files.readString(dir.resolve("backups/loot.yml")));
    }
}
