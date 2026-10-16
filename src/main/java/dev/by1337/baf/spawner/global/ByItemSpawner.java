package dev.by1337.baf.spawner.global;

import dev.by1337.baf.Workflow;
import dev.by1337.item.ItemModel;
import dev.by1337.item.registry.ItemModelHolder;
import dev.by1337.item.registry.ItemRegistry;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.Map;

public final class ByItemSpawner implements GlobalSpawner {
    public static final String SPACE = "baf_airdrop";

    public record Requirements(int minY, int maxY, boolean flatSurface, int radius,
                               int clearHeight, boolean outsideRegions) {
        public static final Requirements DEFAULT = new Requirements(Integer.MIN_VALUE, Integer.MAX_VALUE, false, 0, 1, false);
        public static final YamlDecoder<Requirements> DECODER = RecordYamlDecoder.mapOf(
                Requirements::new,
                YamlDecoder.INT.fieldOf("min_y", Integer.MIN_VALUE),
                YamlDecoder.INT.fieldOf("max_y", Integer.MAX_VALUE),
                YamlDecoder.BOOL.fieldOf("flat_surface", false),
                YamlDecoder.INT.fieldOf("radius", 0),
                YamlDecoder.INT.fieldOf("clear_height", 1),
                YamlDecoder.BOOL.fieldOf("outside_regions", false));

        public Requirements {
            if (minY > maxY) throw new IllegalArgumentException("min_y must be <= max_y");
            if (radius < 0 || radius > 32) throw new IllegalArgumentException("radius must be between 0 and 32");
            if (clearHeight < 1 || clearHeight > 64)
                throw new IllegalArgumentException("clear_height must be between 1 and 64");
        }

        public String failure(Block support) {
            Location at = support.getLocation().add(0, 1, 0);
            int y = at.getBlockY();
            if (y < minY || y > maxY || y < at.getWorld().getMinHeight()
                    || (long) y + clearHeight > at.getWorld().getMaxHeight()) return "height";
            int area = flatSurface ? radius : 0;
            for (int x = -area; x <= area; x++) {
                for (int z = -area; z <= area; z++) {
                    Block ground = support.getRelative(x, 0, z);
                    if (!ground.getType().isSolid() || ground.isPassable()) return "surface";
                    for (int h = 1; h <= clearHeight; h++) {
                        Block above = ground.getRelative(0, h, 0);
                        if (!above.isPassable() || above.isLiquid()) return "surface";
                    }
                }
            }
            if (outsideRegions && !ItemSpawnRegions.isOutside(support, radius, clearHeight)) return "region";
            return null;
        }
    }

    public record SpawnItem(ItemModel itemModel, String workflow, Map<String, String> args,
                            Requirements requirements, boolean consume) implements ItemModelHolder {
        public static final YamlDecoder<SpawnItem> DECODER = DecoderHolder.DECODER;

        private static final class DecoderHolder {
            private static final YamlDecoder<SpawnItem> DECODER = RecordYamlDecoder.mapOf(
                    SpawnItem::new,
                    ItemModel.DECODER.fieldOf("item"),
                    YamlDecoder.STRING.fieldOf("workflow"),
                    YamlDecoder.STRING_TO_STRING.fieldOf("args", Map.of()),
                    Requirements.DECODER.fieldOf("requirements", Requirements.DEFAULT),
                    YamlDecoder.BOOL.fieldOf("consume", true));
        }
    }

    public static final YamlDecoder<ByItemSpawner> DECODER = DecoderHolder.DECODER;

    private static final class DecoderHolder {
        private static final YamlDecoder<ByItemSpawner> DECODER = RecordYamlDecoder.mapOf(
                ByItemSpawner::new, ItemRegistry.decoder(SPACE, SpawnItem.DECODER).fieldOf("items"));
    }

    private final ItemRegistry<SpawnItem> items;

    public ByItemSpawner(ItemRegistry<SpawnItem> items) {
        this.items = items;
    }

    public ItemRegistry<SpawnItem> items() {
        return items;
    }

    @Override
    public void start(SpawnerContext context) {
        for (SpawnItem item : items) {
            if (!(context.launches().get(item.workflow()) instanceof Workflow))
                throw new IllegalArgumentException("Item spawn requires a workflow: " + item.workflow());
        }
    }

    @Override
    public void tick() {
    }

    @Override
    public boolean isGpsPublish() {
        return false;
    }

    @Override
    public long getSecondsUntilNextEvent() {
        return -1;
    }
}
