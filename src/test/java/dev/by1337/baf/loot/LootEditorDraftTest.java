package dev.by1337.baf.loot;

import dev.by1337.baf.loot.item.ItemHolder;
import dev.by1337.baf.loot.menu.LootEditorDraft;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LootEditorDraftTest {
    private final LootEntry a = new LootEntry(new ItemHolder(new byte[] {1}), 1);
    private final LootEntry b = new LootEntry(new ItemHolder(new byte[] {2}), 2);
    private final LootEntry c = new LootEntry(new ItemHolder(new byte[] {3}), 3);

    @Test void fullPageExposesEmptyPageForInsertion() {
        LootEditorDraft draft = new LootEditorDraft(List.of(a, b), 2);
        assertEquals(1, draft.maxPage());
        assertEquals(Arrays.asList(null, null), draft.page(1));
        draft.replacePage(1, Arrays.asList(c, null));
        assertEquals(List.of(a, b, c), draft.items());
        assertEquals(1, draft.maxPage());
    }

    @Test void removingFirstPageSlotDoesNotMoveOtherPageEntries() {
        LootEditorDraft draft = new LootEditorDraft(List.of(a, b, c), 2);
        draft.replacePage(0, Arrays.asList(null, b));
        assertSame(c, draft.page(1).get(0));
        assertEquals(List.of(b, c), draft.items());
        draft.replacePage(1, Arrays.asList(null, null));
        assertEquals(List.of(b), draft.items());
        assertEquals(1, draft.maxPage());
    }

    @Test void weightsTravelWithEntriesAcrossPages() {
        LootEditorDraft draft = new LootEditorDraft(List.of(a, b, c), 2);
        draft.replacePage(0, Arrays.asList(c, b));
        draft.replacePage(1, Arrays.asList(a, null));
        assertEquals(List.of(c, b, a), draft.items());
        assertEquals(3, draft.page(0).get(0).weight());
        assertEquals(1, draft.page(1).get(0).weight());
    }

    @Test void emptyDraftHasOnePageAndPageReadsAreCopies() {
        LootEditorDraft draft = new LootEditorDraft(List.of(), 2);
        assertEquals(0, draft.maxPage());
        List<LootEntry> page = draft.page(0);
        page.set(0, a);
        assertTrue(draft.items().isEmpty());
        draft.replacePage(0, page);
        assertEquals(List.of(a), draft.items());
    }
}
