package dev.by1337.baf.generator.impl;

import dev.by1337.baf.BAF;
import dev.by1337.baf.generator.Generator;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.baf.util.material.MaterialTester;
import dev.by1337.baf.world.WorldData;
import dev.by1337.baf.web.NodeSchema;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class RandomGenerator implements Generator {
    public static final NodeSchema WEB_SCHEMA = NodeSchema.of("Случайная точка", "Ищет свободную область с подходящими блоками.")
            .integer("radius", "Радиус", 2500, 1, false)
            .integer("min_y", "Минимальная высота", 50, -2048, false)
            .integer("max_y", "Максимальная высота", 100, -2048, false)
            .integer("clear_zone", "Радиус свободной зоны", 4, 1, false)
            .integer("clear_y", "Высота свободной зоны", 3, 0, false)
            .text("air_tester", "Условие свободных блоков", "!solid & !water & !lava", false)
            .text("solid_tester", "Условие опоры", "solid & !ice", false)
            .vector("offsets", "Смещение", "0;0;0", false);
    private final Random random = new Random();
    public static final YamlDecoder<RandomGenerator> DECODER = RecordYamlDecoder.mapOf(
            RandomGenerator::new,
            YamlDecoder.INT.fieldOf("radius", 2500),
            YamlDecoder.INT.fieldOf("min_y", 50),
            YamlDecoder.INT.fieldOf("max_y", 100),
            YamlDecoder.INT.fieldOf("clear_zone", 4),
            YamlDecoder.INT.fieldOf("clear_y", 3),
            MaterialTester.DECODER.fieldOf("air_tester", m -> true),
            MaterialTester.DECODER.fieldOf("solid_tester", m -> true),
            Vec3i.DECODER.fieldOf("offsets", Vec3i.ZERO)
    );

    private final int radius;
    private final int min_y;
    private final int max_y;
    private final int clear_zone;
    private final int clear_y;
    private final BitSet air_tester;
    private final BitSet solid_tester;
    private final Vec3i offsets;

    public RandomGenerator(int radius, int minY, int maxY, int clearZone, int clearY, MaterialTester.Tester airTester, MaterialTester.Tester solidTester, Vec3i offsets) {
        this.radius = radius;
        min_y = minY;
        max_y = maxY;
        clear_zone = clearZone;
        clear_y = clearY;
        air_tester = airTester.compile();
        solid_tester = solidTester.compile();
        this.offsets = offsets;
    }

    @Override
    public CompletableFuture<@Nullable Location> tryGenerate(World world) {
        WorldData data = BAF.worldData().getIn(world);

        int x = radius - random.nextInt(radius);
        int z = radius - random.nextInt(radius);

        int layersSize = square(clear_zone * 2);
        int reqBlocks = layersSize - 3;
        int minX = x - clear_zone;
        int maxX = x + clear_zone;
        int minZ = z - clear_zone;
        int maxZ = z + clear_zone;

        return data.collectChunks(minX, minZ, maxX, maxZ).thenApply(chunkMap -> {
            WorldData.LayerResult layer = new WorldData.LayerResult(air_tester, solid_tester);

            int freeLayers = 0;
            for (int y = max_y; y > min_y; y--) {
                data.countOfBlocks(y, minX, minZ, maxX, maxZ, layer, chunkMap);
                if (layer.airs >= reqBlocks) {
                    freeLayers++;
                } else if (layer.solid >= reqBlocks && freeLayers >= clear_y) {
                    return new Location(world, x + offsets.x(), y + 1 + offsets.y(), z + offsets.z());
                } else {
                    freeLayers = 0;
                }
            }
            System.out.println("SKIP " + x + " " + z);
            return null;
        });
    }

    public static int square(int num) {
        return num * num;
    }
}
