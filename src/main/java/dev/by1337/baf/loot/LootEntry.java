package dev.by1337.baf.loot;

import dev.by1337.baf.loot.item.ItemHolder;
import dev.by1337.plc.PlaceholderResolver;
import dev.by1337.plc.Placeholders;
import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;

public record LootEntry(ItemHolder itemHolder, double weight) {
    public LootEntry {
        java.util.Objects.requireNonNull(itemHolder);
        if (!Double.isFinite(weight) || weight < 0) {
            throw new IllegalArgumentException("Вес должен быть конечным неотрицательным числом");
        }
    }
    private static final PlaceholderResolver<LootEntry> PLACEHOLDERS = Placeholders.<LootEntry>create()
            .withContext("weight", v -> v.weight);

    public static final YamlCodec<LootEntry> CODEC = RecordYamlCodecBuilder.mapOf(
            LootEntry::new,
            ItemHolder.CODEC.fieldOf("item", LootEntry::itemHolder, ItemHolder.DIRT),
            YamlCodec.DOUBLE.fieldOf("weight", v -> v.weight, 100D)
    );

    public <T> PlaceholderResolver<T> placeholders() {
        return PLACEHOLDERS.bindCtx(this);
    }
}
