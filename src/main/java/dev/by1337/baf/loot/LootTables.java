package dev.by1337.baf.loot;

import dev.by1337.baf.loot.item.ItemHolder;
import dev.by1337.baf.util.FileBackuper;
import dev.by1337.yaml.YamlMap;
import dev.by1337.yaml.codec.YamlCodec;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class LootTables {
    public static final LootTables DEFAULT = new LootTables();
    private static final YamlCodec<Map<String, LootTable>> CODEC = YamlCodec.mapOf(YamlCodec.STRING, LootTable.CODEC);
    private final Map<String, LootTable> tables = new LinkedHashMap<>();
    private final Map<LootTable, String> ids = new IdentityHashMap<>();
    private final Map<LootTable, Object> editors = new IdentityHashMap<>();
    private File file;

    public LootTables() {
    }

    public LootTables(Map<String, LootTable> tables) {
        tables.forEach(this::set);
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(tables.keySet());
    }

    public String getId(LootTable table) {
        return ids.get(table);
    }

    public LootTable get(String id) {
        return tables.get(id);
    }

    public LootTable set(String id, LootTable table) {
        validateId(id);
        Objects.requireNonNull(table);
        String previousId = ids.get(table);
        if (previousId != null && !previousId.equals(id)) {
            throw new IllegalArgumentException("Таблица уже зарегистрирована как " + previousId);
        }
        LootTable old = tables.get(id);
        if (old == table) return old;
        requireUnlocked(old);
        tables.put(id, table);
        if (old != null) ids.remove(old);
        ids.put(table, id);
        return old;
    }

    public LootTable remove(String id) {
        LootTable table = tables.get(id);
        requireUnlocked(table);
        tables.remove(id);
        if (table != null) ids.remove(table);
        return table;
    }

    public void clear() {
        if (!editors.isEmpty()) throw new IllegalStateException("Закройте редакторы таблиц перед перезагрузкой");
        tables.clear();
        ids.clear();
    }

    public void beginEdit(LootTable table, Object editor) {
        if (!ids.containsKey(table)) throw new IllegalArgumentException("Таблица не зарегистрирована");
        if (editors.putIfAbsent(table, editor) != null) {
            throw new IllegalStateException("Эту таблицу уже редактируют");
        }
    }

    public void endEdit(LootTable table, Object editor) {
        if (editors.get(table) == editor) editors.remove(table);
    }

    public void requireUnlocked(LootTable table) {
        if (editors.containsKey(table)) throw new IllegalStateException("Сначала закройте редактор этой таблицы");
    }

    public static void validateId(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_./:-]+")) {
            throw new IllegalArgumentException("ID может содержать буквы, цифры и _ . / : -");
        }
    }

    public void load(File file) {
        if (!editors.isEmpty()) throw new IllegalStateException("Сначала закройте редакторы таблиц");
        Map<String, LootTable> loaded;
        if (file.exists()) {
            try {
                loaded = YamlMap.load(file).get().decode(CODEC).getOrThrow();
                loaded.keySet().forEach(LootTables::validateId);
            } catch (Exception e) {
                File backupFolder = new File(file.getAbsoluteFile().getParentFile(), "backups");
                backupFolder.mkdirs();
                File backup = FileBackuper.backup(file, backupFolder);
                throw new IllegalStateException("Не удалось загрузить лут. Резервная копия: " + backup, e);
            }
        } else {
            loaded = Map.of("loot/default", new LootTable(List.of(new LootEntry(ItemHolder.DIRT, 100)), 5));
        }
        clear();
        loaded.forEach(this::set);
        this.file = file;
        if (!file.exists()) save();
    }

    public void save() {
        if (file == null) throw new IllegalStateException("Файл таблиц лута не настроен");
        Path temporary = null;
        try {
            Path target = file.toPath().toAbsolutePath();
            Files.createDirectories(target.getParent());
            String yaml = CODEC.encode(tables).asYamlMap().getOrThrow().saveToString();
            temporary = Files.createTempFile(target.getParent(), "loot-", ".tmp");
            Files.writeString(temporary, yaml, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить таблицы лута", e);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }
}
