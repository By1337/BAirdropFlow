package dev.by1337.baf.world;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dev.by1337.baf.generator.ChunkData;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.BitSet;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WorldData implements Listener {
    private static final Executor CHUNK_IO_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("baf-chunk-io-%d").build());
    private static final Logger log = LoggerFactory.getLogger(WorldData.class);
    private final World world;
    private final Cache<Long, RegionFile> regions;
    private final Cache<Long, ChunkData> chunks;
    private final File dataFolder;
    private volatile boolean closing = false;

    public WorldData(World bukkit, Plugin plugin) {
        this.world = bukkit;
        dataFolder = new File(world.getWorldFolder(), "baf_chunks");
        regions = Caffeine.newBuilder()
                .removalListener((RemovalListener<Long, RegionFile>) (key, value, cause) -> {
                    if (value != null) {
                        try {
                            value.close();
                        } catch (IOException e) {
                            log.error("Failed to close region file", e);
                        }
                    }
                })
                .maximumSize(128)
                .build();
        chunks = Caffeine.newBuilder()
                .maximumWeight(256 * 1024 * 1024)
                .weigher((Long key, ChunkData value) -> value.blocks.length * 2)
                .build();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void close() {
        closing = true;
        HandlerList.unregisterAll(this);
        regions.invalidateAll();
        chunks.invalidateAll();
    }

    public static class LayerResult {
        public int airs;
        public int solid;
        public final BitSet airTester;
        public final BitSet solidTester;

        public LayerResult(BitSet airTester, BitSet solidTester) {
            this.airTester = airTester;
            this.solidTester = solidTester;
        }
    }

    public boolean isUnloadedChunks(int minX, int minZ, int maxX, int maxZ) {
        int startChunkX = minX >> 4;
        int startChunkZ = minZ >> 4;
        int endChunkX = maxX >> 4;
        int endChunkZ = maxZ >> 4;

        for (int cx = startChunkX; cx <= endChunkX; cx++) {
            for (int cz = startChunkZ; cz <= endChunkZ; cz++) {
                if (world.isChunkLoaded(cx, cz)) return false;
            }
        }
        return true;
    }

    public void countOfBlocks(int y, int minX, int minZ, int maxX, int maxZ, LayerResult result) {
        result.airs = 0;
        result.solid = 0;

        int startChunkX = minX >> 4;
        int startChunkZ = minZ >> 4;
        int endChunkX = maxX >> 4;
        int endChunkZ = maxZ >> 4;

        for (int cx = startChunkX; cx <= endChunkX; cx++) {
            for (int cz = startChunkZ; cz <= endChunkZ; cz++) {

                ChunkData chunk = loadChunkData(cx, cz);

                int localMinX = (cx == startChunkX) ? (minX & 15) : 0;
                int localMaxX = (cx == endChunkX) ? (maxX & 15) : 15;
                int localMinZ = (cz == startChunkZ) ? (minZ & 15) : 0;
                int localMaxZ = (cz == endChunkZ) ? (maxZ & 15) : 15;

                for (int localX = localMinX; localX <= localMaxX; localX++) {
                    for (int localZ = localMinZ; localZ <= localMaxZ; localZ++) {
                        short type = chunk.getType(localX, y, localZ);
                        if (result.airTester.get(type)) {
                            result.airs++;
                        } else if (result.solidTester.get(type)) {
                            result.solid++;
                        }
                    }
                }
            }
        }
    }

    private ChunkData loadChunkData(int x, int z) {
        ChunkData chunk = getChunk(x, z);
        if (chunk != null) return chunk;
        System.out.println("miss " + x + " " + z);
        var v = world.getChunkAt(x, z);
        chunk = new ChunkData();
        chunk.load(v);
        chunks.put(asLong(x, z), chunk);
        ChunkData finalChunk = chunk;
        CHUNK_IO_EXECUTOR.execute(() -> saveChunk(finalChunk));
        return chunk;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (closing) return;
        if (event.getWorld() != world) return;
        ChunkData data = new ChunkData();
        data.load(event.getChunk());
        CHUNK_IO_EXECUTOR.execute(() -> saveChunk(data));
    }

    private RegionFile getRegionFile(int chunkX, int chunkZ) {
        int regionX = chunkX >> 4;
        int regionZ = chunkZ >> 4;
        return regions.get(asLong(regionX, regionZ), k -> {
            try {
                return new RegionFile(regionX, regionZ, dataFolder);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public @Nullable ChunkData getChunk(int x, int z) {
        return chunks.get(asLong(x, z), k -> {
            try {
                return getRegionFile(x, z).read(x, z);
            } catch (IOException e) {
                log.error("Failed to read chunk {} {}", x, z, e);
                return null;
            }
        });
    }

    public void saveChunk(ChunkData chunk) {
        if (closing) return;
        try {
            getRegionFile(chunk.x, chunk.z).write(chunk);
        } catch (IOException e) {
            log.error("Failed to store chunk", e);
        }
    }

    public static long asLong(int x, int z) {
        return x & 4294967295L | (z & 4294967295L) << 32;
    }

    public static int getX(long chunkAsLong) {
        return (int) (chunkAsLong & 4294967295L);
    }

    public static int getZ(long chunkAsLong) {
        return (int) (chunkAsLong >>> 32 & 4294967295L);
    }

}
