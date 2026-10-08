package dev.by1337.baf.util;

import dev.by1337.yaml.decoder.InlineYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Vec3i(int x, int y, int z) {
    public static final Vec3i ZERO = new Vec3i(0, 0, 0);
    public static final YamlDecoder<Vec3i> DECODER = InlineYamlDecoder.inline(
            ";",
            "<x>;<y>;<z>",
            Vec3i::new,
            YamlDecoder.INT.fieldOf(),
            YamlDecoder.INT.fieldOf(),
            YamlDecoder.INT.fieldOf()
    );

    public boolean isZero() {
        return x == 0 && y == 0 && z == 0;
    }

    public Block toBlock(@NotNull World world) {
        return world.getBlockAt(this.x, this.y, this.z);
    }

    public long asLong() {
        return asLong(x, y, z);
    }

    public static long asLong(Vec3i pos) {
        return asLong(pos.x, pos.y, pos.z);
    }

    public static long asLong(int x, int y, int z) {
        return ((long) x & 67108863L) << 38 | (long) y & 4095L | ((long) z & 67108863L) << 12;
    }

    public static int getX(long packedPos) {
        return (int) (packedPos >> 38);
    }

    public static int getY(long packedPos) {
        return (int) (packedPos << 52 >> 52);
    }

    public static int getZ(long packedPos) {
        return (int) (packedPos << 26 >> 38);
    }


    public Location toLocation(@Nullable World world) {
        return new Location(world, this.x, this.y, this.z);
    }

    @Contract(
            value = "_, _, _ -> new",
            pure = true
    )
    public Vec3i sub(int x1, int y1, int z1) {
        return new Vec3i(this.x - x1, this.y - y1, this.z - z1);
    }

    @Contract(
            value = "_, _, _ -> new",
            pure = true
    )
    public Vec3i add(int x1, int y1, int z1) {
        return new Vec3i(this.x + x1, this.y + y1, this.z + z1);
    }

    @Contract(
            value = "_ -> new",
            pure = true
    )
    public Vec3i add(Vec3i t1) {
        return new Vec3i(this.x + t1.x, this.y + t1.y, this.z + t1.z);
    }

    @Contract(
            value = "_ -> new",
            pure = true
    )
    public Vec3i sub(Vec3i t1) {
        return new Vec3i(this.x - t1.x, this.y - t1.y, this.z - t1.z);
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int z() {
        return this.z;
    }

    public int hashCode() {
        long bits = 7L;
        bits = 31L * bits + (long) this.x;
        bits = 31L * bits + (long) this.y;
        bits = 31L * bits + (long) this.z;
        return (int) (bits ^ bits >> 32);
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof Vec3i)) {
            return false;
        } else {
            Vec3i v = (Vec3i) obj;
            return this.x == v.x && this.y == v.y && this.z == v.z;
        }
    }
}
