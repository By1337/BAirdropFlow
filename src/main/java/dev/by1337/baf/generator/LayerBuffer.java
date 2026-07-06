package dev.by1337.baf.generator;

import java.util.Arrays;

public final class LayerBuffer {
    public final int chunkWidth;
    public final int chunkHeight;

    // chunkWidth * chunkHeight * 4
    public final long[] bits;

    public LayerBuffer(int chunkWidth, int chunkHeight) {
        this.chunkWidth = chunkWidth;
        this.chunkHeight = chunkHeight;
        this.bits = new long[chunkWidth * chunkHeight * 4];
    }

    public void clear() {
        Arrays.fill(bits, 0L);
    }

}
