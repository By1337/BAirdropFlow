package dev.by1337.baf.loot.menu;

import dev.by1337.item.ItemComponents;
import dev.by1337.item.ItemModel;
import dev.by1337.item.component.impl.ItemLoreComponent;
import dev.by1337.item.util.text.RawTextComponentLike;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Compatibility with ConfigurableItems 1.6, which caches text containing placeholders. */
final class LootMenuText {
    private LootMenuText() {}

    static void restore(ItemModel model) {
        var components = model.components();
        ComponentLike name = model.get(ItemComponents.NAME);
        if (name != null) components.set(ItemComponents.NAME, dynamicText(name));
        ItemLoreComponent lore = model.get(ItemComponents.LORE);
        if (lore != null) {
            var lines = lore.lore().stream().map(LootMenuText::dynamicText).toList();
            if (!lines.equals(lore.lore())) components.set(ItemComponents.LORE, new ItemLoreComponent(lines));
        }
    }

    static ComponentLike dynamicText(ComponentLike text) {
        if (text instanceof RawTextComponentLike) return text;
        String source = MiniMessage.miniMessage().serialize(text.asComponent());
        if (!source.contains("{") && !source.contains("%")) return text;
        return new RawTextComponentLike(source);
    }
}
