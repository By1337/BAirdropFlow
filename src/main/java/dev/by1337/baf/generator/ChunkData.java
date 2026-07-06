package dev.by1337.baf.generator;

import dev.by1337.baf.util.zip.CRC32Util;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.CRC32;

public class ChunkData {
    public static final short AIR = (short) Material.AIR.ordinal();
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 320;
    public static final int MAX_SECTIONS = 24;
    static final int BLOCKS = 24 * 16 * 16 * 16;
    static final int CHUNK_DATA_SIZE = BLOCKS * Short.BYTES;
    public static final int CHUNK_RECORD_SIZE =
            1 + Integer.BYTES +
                    CHUNK_DATA_SIZE;


    private static final Material[] MATERIALS = Material.values();
    private static final Logger log = LoggerFactory.getLogger(ChunkData.class);
    public int x, z;
    public short[] blocks;
    public int minY;
    public int maxY;
    private int sectionCount;

    public void save(DataOutput out) throws IOException {
        CRC32 crc = new CRC32();
        out.writeInt(CRC32Util.updateInt(crc, x));
        out.writeInt(CRC32Util.updateInt(crc, z));
        out.writeInt(CRC32Util.updateInt(crc, minY));
        out.writeInt(CRC32Util.updateInt(crc, maxY));
        out.writeInt(CRC32Util.updateInt(crc, sectionCount));

        for (short block : blocks) {
            out.writeShort(CRC32Util.updateShort(crc, block));
        }
        out.writeInt((int) crc.getValue());
    }

    public boolean load(DataInput in) throws IOException {
        CRC32 crc = new CRC32();
        x = CRC32Util.updateInt(crc, in.readInt());
        z = CRC32Util.updateInt(crc, in.readInt());
        minY = CRC32Util.updateInt(crc, in.readInt());
        maxY = CRC32Util.updateInt(crc, in.readInt());
        sectionCount = CRC32Util.updateInt(crc, in.readInt());
        if (sectionCount < 0 || sectionCount > 24) return false;
        blocks = new short[sectionCount << 12];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = CRC32Util.updateShort(crc, in.readShort());
        }
        int originalCrc = in.readInt();
        var v = originalCrc == (int) crc.getValue();
        if (!v) System.out.println("BAD crc");
        return v;
    }

    public void load(Chunk chunk) {
        var snapshot = chunk.getChunkSnapshot(true, false, false, false);
        x = snapshot.getX();
        z = snapshot.getZ();

        var world = chunk.getWorld();
        minY = world.getMinHeight();
        maxY = world.getMaxHeight();

        int height = maxY - minY;
        sectionCount = (int) Math.ceil(height / 16.0);
        blocks = new short[sectionCount << 12]; // sectionCount * 4096

        Arrays.fill(blocks, AIR);

        for (int y = minY; y < maxY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    Material material = snapshot.getBlockType(x, y, z);
                    blocks[getIndex(x, y, z)] = (short) material.ordinal();
                }
            }
        }
    }

    public Material getMaterial(int x, int y, int z) {
        return MATERIALS[getType(x, y, z)];
    }

    public short getType(int x, int y, int z) {
        int index = getIndex(x, y, z);
        if (index < 0 || index >= blocks.length) {
            return AIR;
        }
        return blocks[index];
    }


    public boolean isEmpty() {
        for (short block : blocks) {
            if (block != AIR) {
                return false;
            }
        }
        return true;
    }

    private int getIndex(int x, int y, int z) {
        int sectionIndex = (y - minY) >> 4;
        int localY = y & 15;
        return (sectionIndex << 12) | (localY << 8) | (z << 4) | x;
    }
}
