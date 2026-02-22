package dev.by1337.baf.loot;

import dev.by1337.baf.loot.item.ItemHolder;
import dev.by1337.yaml.codec.RecordYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LootTables {
    public static final YamlCodec<LootTables> CODEC = RecordYamlCodecBuilder.mapOf(
            LootTables::new,
            YamlCodec.mapOf(YamlCodec.STRING, LootTable.CODEC)
                    .fieldOf("tables", v -> v.lookup)
    );
    private final Map<String, LootTable> lookup;

    public LootTables(Map<String, LootTable> lookup) {
        this.lookup = lookup;
    }

    public LootTable get(String key) {
        return lookup.get(key);
    }

    public LootTable set(String key, LootTable value) {
        return lookup.put(key, value);
    }

    public static LootTables createDefault(){
        LootTables lootTables = new LootTables(new HashMap<>());
        lootTables.set("loot/default", new LootTable(List.of(new LootEntry(ItemHolder.DIRT, 100)), 5));
        return lootTables;
    }
}
