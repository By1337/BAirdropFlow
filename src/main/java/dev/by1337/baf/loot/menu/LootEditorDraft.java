package dev.by1337.baf.loot.menu;

import dev.by1337.baf.loot.LootEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LootEditorDraft {
    private final int pageSize;
    private final List<LootEntry> entries;

    public LootEditorDraft(List<LootEntry> entries, int pageSize) {
        if (pageSize <= 0) throw new IllegalArgumentException("pageSize");
        this.pageSize = pageSize;
        this.entries = new ArrayList<>(entries);
    }

    public int maxPage() {
        return entries.size() / pageSize;
    }

    public List<LootEntry> page(int page) {
        if (page < 0) throw new IllegalArgumentException("page");
        List<LootEntry> result = new ArrayList<>(pageSize);
        for (int i = 0; i < pageSize; i++) {
            int index = page * pageSize + i;
            result.add(index < entries.size() ? entries.get(index) : null);
        }
        return result;
    }

    public void replacePage(int page, List<LootEntry> items) {
        if (page < 0 || items.size() != pageSize) throw new IllegalArgumentException("page");
        int start = page * pageSize;
        while (entries.size() < start + pageSize) entries.add(null);
        for (int i = 0; i < pageSize; i++) entries.set(start + i, items.get(i));
        while (!entries.isEmpty() && entries.get(entries.size() - 1) == null) entries.remove(entries.size() - 1);
    }

    public List<LootEntry> items() {
        return entries.stream().filter(Objects::nonNull).toList();
    }
}
