package dev.by1337.baf.util.zip;

import java.util.zip.CRC32;

public class CRC32Util {

    public static int updateInt(CRC32 crc, int v) {
        crc.update((byte) (0xff & (v >> 24)));
        crc.update((byte) (0xff & (v >> 16)));
        crc.update((byte) (0xff & (v >> 8)));
        crc.update((byte) (0xff & v));
        return v;
    }

    public static short updateShort(CRC32 crc, short v) {
        crc.update((byte) (0xff & (v >> 8)));
        crc.update((byte) (0xff & v));
        return v;
    }
}
