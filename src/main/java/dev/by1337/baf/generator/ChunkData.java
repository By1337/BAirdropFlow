package dev.by1337.baf.generator;

import org.bukkit.Chunk;
import org.bukkit.Material;

public class ChunkData {
    public int x, z;
    public Material[] blocks;
    public transient long[][] layers;
    public int min;
    public int max;

    public void load(Chunk chunk) {
        var snapshot = chunk.getChunkSnapshot(true, false, false, false);

        x = snapshot.getX();
        z = snapshot.getZ();

        var world = chunk.getWorld();
        min = world.getMinHeight();
        max = world.getMaxHeight();

        int height = max - min;

        blocks = new Material[height * 256];

        for (int y = min; y < max; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    blocks[getBlockIndex(x, y, z)] = snapshot.getBlockType(x, y, z);
                }
            }
        }

        buildLayers();
    }

    private void buildLayers() {
        layers = new long[max - min][4];

        for (int y = min; y < max; y++) {
            long[] layer = layers[y - min];

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {

                    Material material = blocks[getBlockIndex(x, y, z)];

                    if (!material.isSolid())
                        continue;

                    int bit = (z << 4) | x;

                    layer[bit >>> 6] |= 1L << (bit & 63);
                }
            }
        }
    }

    private int getBlockIndex(int x, int y, int z) {
        return ((y - min) << 8) | (z << 4) | x;
    }

    private int getSectionIndex(int y) {
        return (y - min) >> 4;
    }
}
