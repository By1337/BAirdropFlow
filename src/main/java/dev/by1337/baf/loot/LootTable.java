package dev.by1337.baf.loot;

import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class LootTable {
    public static final YamlCodec<LootTable> CODEC = RecordYamlCodecBuilder.mapOf(
            LootTable::new,
            LootEntry.CODEC.listOf().fieldOf("items", v -> v.items),
            YamlCodec.INT.fieldOf("rolls", v -> v.rolls)
    );
    private List<LootEntry> items;
    private int rolls;
    private ItemSelector selector;


    public LootTable(List<LootEntry> items, int rolls) {
        this.items = items;
        this.rolls = rolls;
    }

    public List<LootEntry> items() {
        return Collections.unmodifiableList(items);
    }

    public void setItems(List<LootEntry> items) {
        this.items = items;
        selector = null;
    }

    public List<ItemStack> generate() {
        if (items.isEmpty()) return List.of();
        if (selector == null) selector = new ItemSelector();
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            var v = selector.getRandom();
            if (v == null) continue;
            result.add(v.itemHolder().getItemStack());
        }
        return result;
    }

    public class ItemSelector {
        private static final Random random = new Random();
        private double totalWeight;

        public ItemSelector() {
            for (LootEntry item : items) {
                totalWeight += item.weight();
            }
        }

        public LootEntry getRandom() {
            if (items.isEmpty()) return null;

            double randomValue = random.nextDouble() * totalWeight;
            double cumulativeWeight = 0;

            for (LootEntry item : items) {
                cumulativeWeight += item.weight();
                if (randomValue < cumulativeWeight) {
                    return item;
                }
            }
            return null;
        }
    }
}
