package dev.by1337.baf.loot;

import dev.by1337.baf.loot.item.ItemHolder;
import dev.by1337.plc.PlaceholderResolver;
import dev.by1337.plc.Placeholders;
import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public record LootEntry(ItemHolder itemHolder, double weight, int minCount, int maxCount) {
    public LootEntry(ItemHolder itemHolder, double weight) {
        this(itemHolder, weight, 0, 0);
    }

    public LootEntry {
        java.util.Objects.requireNonNull(itemHolder);
        if (!Double.isFinite(weight) || weight < 0) {
            throw new IllegalArgumentException("Вес должен быть конечным неотрицательным числом");
        }
        if (minCount < 0 || maxCount < 0 || (minCount != 0 && maxCount != 0 && minCount > maxCount)) {
            throw new IllegalArgumentException("Количество должно быть неотрицательным, минимум не больше максимума");
        }
    }

    public ItemStack generate() {
        ItemStack stack = itemHolder.getItemStack();
        if (minCount != 0 && maxCount != 0) {
            int c = (int) ThreadLocalRandom.current().nextLong(minCount, (long) maxCount + 1);
            stack.setAmount(Math.clamp(c, 1, stack.getMaxStackSize()));
        }
        return stack;
    }

    private static final PlaceholderResolver<LootEntry> PLACEHOLDERS = Placeholders.<LootEntry>create()
            .withContext("weight", v -> v.weight)
            .withContext("min_count", v -> v.minCount)
            .withContext("max_count", v -> v.maxCount)
            .withContext("random_count", v -> v.minCount != 0 && v.maxCount != 0);

    public static final YamlCodec<LootEntry> CODEC = RecordYamlCodecBuilder.mapOf(
            LootEntry::new,
            ItemHolder.CODEC.fieldOf("item", LootEntry::itemHolder, ItemHolder.DIRT),
            YamlCodec.DOUBLE.fieldOf("weight", v -> v.weight, 100D),
            YamlCodec.INT.fieldOf("min", v -> v.minCount, 0),
            YamlCodec.INT.fieldOf("max", v -> v.maxCount, 0)
    );

    public <T> PlaceholderResolver<T> placeholders() {
        return PLACEHOLDERS.bindCtx(this);
    }
}
