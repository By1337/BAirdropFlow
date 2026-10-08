package dev.by1337.baf.loot;

import dev.by1337.bmenu.random.WeightedItem;
import dev.by1337.bmenu.random.WeightedItemSelector;
import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class LootTable {
    public static final YamlCodec<LootTable> CODEC = RecordYamlCodecBuilder.mapOf(
            LootTable::new,
            LootEntry.CODEC.listOf().fieldOf("items", LootTable::items, List.of())
    );
    private List<LootEntry> items;
    private WeightedItemSelector<LootEntry> selector;
    private boolean selectorReady;

    public LootTable(List<LootEntry> items) {
        setItems(items);
    }

    public List<LootEntry> items() {
        return items;
    }

    public void setItems(List<LootEntry> items) {
        this.items = List.copyOf(items);
        selector = null;
        selectorReady = false;
    }

    public LootEntry selectItem() {
        if (!selectorReady) {
            double max = items.stream().mapToDouble(LootEntry::weight).max().orElse(0);
            if (max > 0) {
                // Normalize before summing so large finite weights cannot overflow.
                var weighted = items.stream().filter(v -> v.weight() > 0)
                        .map(v -> WeightedItem.of(v.weight() / max, v)).toList();
                selector = new WeightedItemSelector<>(weighted);
            }
            selectorReady = true;
        }
        return selector == null ? null : selector.getRandomItem();
    }

    public List<ItemStack> generate(int rolls) {
        if (rolls < 0) throw new IllegalArgumentException("Количество прокруток не может быть отрицательным");
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            LootEntry entry = selectItem();
            if (entry == null) break;
            result.add(entry.generate());
        }
        return result;
    }
}
