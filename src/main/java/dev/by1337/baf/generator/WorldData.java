package dev.by1337.baf.generator;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.World;

import java.util.BitSet;

public class WorldData {
    private final World bukkit;
    private final Long2ObjectOpenHashMap<ChunkData> chunks = new Long2ObjectOpenHashMap<>();

    public WorldData(World bukkit) {
        this.bukkit = bukkit;
    }


    public static long asLong(int x, int z) {
        return x & 4294967295L | (z & 4294967295L) << 32;
    }
    public static int getX(long chunkAsLong) {
        return (int)(chunkAsLong & 4294967295L);
    }

    public static int getZ(long chunkAsLong) {
        return (int)(chunkAsLong >>> 32 & 4294967295L);
    }

}
