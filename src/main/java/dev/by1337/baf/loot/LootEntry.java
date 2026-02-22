package dev.by1337.baf.loot;

import dev.by1337.baf.loot.item.ItemHolder;
import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;

public record LootEntry(ItemHolder itemHolder, double weight) {
    public static final YamlCodec<LootEntry> CODEC = RecordYamlCodecBuilder.mapOf(
            LootEntry::new,
            ItemHolder.CODEC.fieldOf("item", LootEntry::itemHolder, ItemHolder.DIRT),
            YamlCodec.DOUBLE.fieldOf("weight", v -> v.weight, 100D)
    );
}
