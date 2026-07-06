/*
package dev.by1337.baf.generator.impl;

import dev.by1337.baf.BAF;
import dev.by1337.baf.generator.Generator;
import dev.by1337.baf.world.WorldData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public class TestGenerator implements Generator {
    private final Random random = new Random();

    @Override
    public @Nullable Location tryGenerate(World world) {
        WorldData data = BAF.worldData().getWorldData(world);

        int x = 500 - random.nextInt(500);
        int z = 500 - random.nextInt(500);

        int size = 5;
        int layersSize = square(size * 2);
        int reqBlocks = layersSize - 3;
        int minX = x - size;
        int maxX = x + size;
        int minZ = z - size;
        int maxZ = z + size;

        // if (!data.isUnloadedChunks(minX, minZ, maxX, maxZ)) return null;

        WorldData.LayerResult layer = new WorldData.LayerResult();
        layer.airTester = m -> !m.isSolid() && m != Material.WATER && m != Material.ICE;
        layer.solidTester = m -> m.isSolid() && m != Material.ICE;

        int freeLayers = 0;
        for (int y = 100; y > world.getMinHeight(); y--) {
            data.countOfBlocks(y, minX, minZ, maxX, maxZ, layer);
            if (layer.airs >= reqBlocks) {
                freeLayers++;
            } else if (layer.solid >= reqBlocks && freeLayers >= 2) {
                return new Location(world, x, y + 1, z);
            } else {
                freeLayers = 0;
            }
        }

        return null;
    }

    public static int square(int num) {
        return num * num;
    }
}
*/
