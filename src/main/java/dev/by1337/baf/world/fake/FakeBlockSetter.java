package dev.by1337.baf.world.fake;

import dev.by1337.baf.util.Vec3i;
import dev.by1337.core.BCore;
import dev.by1337.core.bridge.world.BlockEntityUtil;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Arrays;

public class FakeBlockSetter implements Listener, Closeable {
    private static final BlockEntityUtil BLOCK_ENTITY_UTIL = BCore.getBlockEntityUtil();
    private static final Logger log = LoggerFactory.getLogger("BTraps");
    private final Long2ObjectOpenHashMap<BlockEntityUtil.BlockInfo> originals = new Long2ObjectOpenHashMap<>();
    private final Long2IntOpenHashMap changesByChunk = new Long2IntOpenHashMap();
    private final Plugin plugin;
    private final World world;
    private final String worldName;
    private final int maxY;
    private final int minY;
    private final String nbtNamespace;
    private final Location location;

    public FakeBlockSetter(Plugin plugin, World world) {
        location = new Location(world, 0, 0, 0);
        this.plugin = plugin;
        this.world = world;
        worldName = world.getName();
        maxY = world.getMaxHeight();
        minY = world.getMinHeight();
        changesByChunk.defaultReturnValue(0);
        nbtNamespace = new NamespacedKey(plugin, "null").getNamespace();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void set(int x, int y, int z, BlockData blockData) {
        if (!isInBound(y)) return;
        long packedPos = Vec3i.asLong(x, y, z);
        boolean currentIsFake = originals.containsKey(packedPos);
        location.set(x, y, z);
        //Location loc = new Location(world, x, y, z);
        if (!currentIsFake) {
            var orig = BLOCK_ENTITY_UTIL.getBlock(location);
            writeToPdc(location, orig, x, y, z);
            originals.put(Vec3i.asLong(x, y, z), orig);

            Chunk chunk = location.getChunk();
            long chunkPos = asLong(chunk.getX(), chunk.getZ());
            int diff = changesByChunk.get(chunkPos);
            if (diff == 0) {
                chunk.addPluginChunkTicket(plugin);
            }
            changesByChunk.put(chunkPos, diff + 1);
        }
        BLOCK_ENTITY_UTIL.tryClear(location);
        location.getBlock().setBlockData(blockData, false);
    }

    public void undo(int x, int y, int z) {
        if (!isInBound(y)) return;
        // Location loc = new Location(world, x, y, z);
        location.set(x, y, z);
        long blockPos = Vec3i.asLong(x, y, z);
        var original = originals.remove(blockPos);
        if (original != null) {
            BLOCK_ENTITY_UTIL.setBlock(location, original.blockId(), original.blockEntity(), false);
        }
        Chunk chunk = location.getChunk();
        var pdc = chunk.getPersistentDataContainer();
        pdc.remove(new NamespacedKey(plugin, Long.toString(blockPos)));
        long chunkPos = asLong(chunk.getX(), chunk.getZ());
        int diff = changesByChunk.get(chunkPos);
        if (diff == 1) {
            chunk.removePluginChunkTicket(plugin);
            changesByChunk.remove(chunkPos);
        } else {
            changesByChunk.put(chunkPos, diff - 1);
        }
    }

    //0   - 3   : x (int)
    //4   - 7   : y (int)
    //8   - 11  : z (int)
    //12  - 15  : blockId (int)
    //16        : hasEntity (byte)
    //17  - 20  : entityLength (int)  <-- если hasEntity == 1
    //21  - ... : entity bytes
    private void writeToPdc(Location loc, BlockEntityUtil.BlockInfo block, int x, int y, int z) {
        var pdc = loc.getChunk().getPersistentDataContainer();
        byte[] entity = block.blockEntity();
        boolean hasEntity = entity != null && entity.length != 0;

        int size = hasEntity
                ? 16 + 1 + 4 + entity.length
                : 16 + 1;

        byte[] out = new byte[size];

        writeIntBE(x, out, 0);
        writeIntBE(y, out, 4);
        writeIntBE(z, out, 8);
        writeIntBE(block.blockId(), out, 12);

        out[16] = hasEntity ? (byte) 1 : (byte) 0;

        if (hasEntity) {
            writeIntBE(entity.length, out, 17);
            System.arraycopy(entity, 0, out, 21, entity.length);
        }

        pdc.set(
                new NamespacedKey(plugin, Long.toString(Vec3i.asLong(x, y, z))),
                PersistentDataType.BYTE_ARRAY,
                out
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (world != event.getWorld()) return;
        Chunk chunk = event.getChunk();
        restoreFromPdc(chunk.getPersistentDataContainer(), (x, y, z, block) -> BLOCK_ENTITY_UTIL.setBlock(
                location.set(x, y, z),
                block.blockId(),
                block.blockEntity(),
                false
        ));
    }

    private void restoreFromPdc(PersistentDataContainer pdc, Function4 out) {
        for (NamespacedKey key : pdc.getKeys()) {
            if (!key.getNamespace().equals(nbtNamespace)) continue;
            try {
                byte[] data = pdc.get(key, PersistentDataType.BYTE_ARRAY);
                pdc.remove(key);
                if (data == null) continue;
                if (data.length < 17) continue;
                int x = readIntBE(data, 0);
                int y = readIntBE(data, 4);
                int z = readIntBE(data, 8);
                int blockId = readIntBE(data, 12);
                boolean hasEntity = data[16] == 1;
                byte[] entity;
                if (hasEntity) {
                    int entitySize = readIntBE(data, 17);
                    entity = Arrays.copyOfRange(data, 21, 21 + entitySize);
                } else {
                    entity = null;
                }

                out.apply(x, y, z, new BlockEntityUtil.BlockInfo(null, blockId, entity));
            } catch (Exception e) {
                log.error("Failed to restore original block", e);
            }
        }
    }

    public void undoAll() {
        for (long l : originals.keySet().toLongArray()) {
            undo(Vec3i.getX(l), Vec3i.getY(l), Vec3i.getZ(l));
        }
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        undoAll();
    }

    private interface Function4 {
        void apply(int x, int y, int z, BlockEntityUtil.BlockInfo block);
    }

    private boolean isInBound(int y) {
        return y >= minY && y < maxY;
    }

    private static long asLong(int x, int z) {
        return (long) x & 4294967295L | ((long) z & 4294967295L) << 32;
    }

    public static void writeIntBE(int value, byte[] dest, int offset) {
        dest[offset] = (byte) (value >>> 24);
        dest[offset + 1] = (byte) (value >>> 16);
        dest[offset + 2] = (byte) (value >>> 8);
        dest[offset + 3] = (byte) (value);
    }

    public static int readIntBE(byte[] src, int offset) {
        return ((src[offset] & 0xFF) << 24) |
                ((src[offset + 1] & 0xFF) << 16) |
                ((src[offset + 2] & 0xFF) << 8) |
                ((src[offset + 3] & 0xFF));
    }
}
