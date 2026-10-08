package dev.by1337.baf.loot.menu;

import dev.by1337.item.util.text.RawTextComponentLike;
import dev.by1337.plc.Placeholders;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LootMenuTextTest {
    @Test
    void restoresLivePlaceholdersInCachedTextAndPreservesFormatting() {
        Component cached = Component.text("Страница: {current_page}", NamedTextColor.GOLD);
        var restored = LootMenuText.dynamicText(cached);
        assertTrue(restored instanceof RawTextComponentLike);
        var raw = (RawTextComponentLike) restored;
        int[] page = {3};
        var resolver = Placeholders.create().of("current_page", () -> page[0]);
        var text = PlainTextComponentSerializer.plainText();
        Component first = raw.asComponent(resolver.bind(null), null);
        assertEquals("Страница: 3", text.serialize(first));
        assertEquals(NamedTextColor.GOLD, first.color());
        page[0] = 13;
        assertEquals("Страница: 13", text.serialize(raw.asComponent(resolver.bind(null), null)));
        assertSame(raw, LootMenuText.dynamicText(raw));
    }

    @Test
    void keepsStaticTextAndExistingDynamicTextUntouched() {
        Component fixed = Component.text("Сохранить");
        assertSame(fixed, LootMenuText.dynamicText(fixed));
        var raw = new RawTextComponentLike("<gold>{edit_mode}");
        assertSame(raw, LootMenuText.dynamicText(raw));
    }
}
