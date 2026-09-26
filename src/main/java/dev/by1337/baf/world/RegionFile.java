package dev.by1337.baf.world;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

public class RegionFile {
    public static final int MAX_CHUNK_SIZE = 1024 * 200;
    public static final long REGION_SIZE = MAX_CHUNK_SIZE * 256;
    private static final int MAGIC = 0xDEADBEEF;
    private static final Logger log = LoggerFactory.getLogger(RegionFile.class);
    private final File file;
    private final RandomAccessFile accessFile;
    //private final ChunkData[] chunks = new ChunkData[16 * 16];

    public RegionFile(int regionX, int regionZ, File folder) throws IOException {
        folder.mkdirs();
        file = new File(folder, regionX + "_" + regionZ + ".bin");
        accessFile = new RandomAccessFile(file, "rw");
    }

    public synchronized @Nullable ChunkData read(int chunkX, int chunkZ) throws IOException {
        int localChunkX = chunkX & 15;
        int localChunkZ = chunkZ & 15;
        int chunkIndex = (localChunkZ << 4) | localChunkX;
        //ChunkData chunkData = chunks[chunkIndex];
        //if (chunkData != null) return chunkData;
        int offset = MAX_CHUNK_SIZE * chunkIndex;
        if (accessFile.length() < offset + 4) return null;
        accessFile.seek(offset);
        int size = accessFile.readInt();
        if (size < 0 || size > MAX_CHUNK_SIZE) return null;
        if (accessFile.length() < offset + 4 + size) return null;
        int magic = accessFile.readInt();
        if (magic != MAGIC) return null;
        ChunkData data = new ChunkData();
        if (!data.load(accessFile)) return null;
        if (data.x != chunkX || data.z != chunkZ) return null;
        //return chunks[chunkIndex] = data;
        return data;
    }
    public synchronized void write(ChunkData data) throws IOException {
        int localChunkX = data.x & 15;
        int localChunkZ = data.z & 15;
        int chunkIndex = (localChunkZ << 4) | localChunkX;
       // chunks[chunkIndex] = data;
        int offset = MAX_CHUNK_SIZE * chunkIndex;
        accessFile.seek(offset);
        long start = accessFile.getFilePointer();
        accessFile.skipBytes(4);
        accessFile.writeInt(MAGIC);
        var v = new DataOutputStream(new BufferedOutputStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                accessFile.write(b);
            }

            @Override
            public void write(byte[] b) throws IOException {
                accessFile.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                accessFile.write(b, off, len);
            }
        }));
        data.save(v);
        v.close();
        long end = accessFile.getFilePointer();
        int size = (int) (end - start - 4);
        accessFile.seek(offset);
        accessFile.writeInt(size);
    }

    public synchronized void close() throws IOException {
        accessFile.close();
    }
}
