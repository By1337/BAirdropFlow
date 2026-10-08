package dev.by1337.baf.schematic;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import dev.by1337.baf.BAF;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.core.util.misc.Pair;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Contract;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;


public class SchematicFile {
    public static final YamlDecoder<SchematicFile> DECODER = YamlDecoder.STRING.map(s -> new SchematicFile(readFromFile(s)));
    private final List<Pair<Vec3i, BlockData>> blocks;

    public SchematicFile(List<Pair<Vec3i, BlockData>> blocks) {
        this.blocks = blocks.stream().sorted(Comparator.comparingInt(v -> v.getLeft().y())).toList();
    }

    public void forEachPoints(Consumer<Vec3i> consumer) {
        for (Pair<Vec3i, BlockData> block : blocks) {
            consumer.accept(block.getKey());
        }
    }


    @Contract("_ -> new")
    public SchematicFile removeIf(Predicate<Material> filter) {
        List<Pair<Vec3i, BlockData>> result = new ArrayList<>(blocks.size());
        for (Pair<Vec3i, BlockData> pair : blocks) {
            if (filter.test(pair.getRight().getMaterial())) continue;
            result.add(pair);
        }
        return new SchematicFile(result);
    }

    @Contract("_ -> new")
    public SchematicFile mutatePos(Function<Vec3i, Vec3i> mutate) {
        List<Pair<Vec3i, BlockData>> result = new ArrayList<>(blocks.size());
        for (Pair<Vec3i, BlockData> pair : blocks) {
            result.add(Pair.of(mutate.apply(pair.getLeft()), pair.getRight()));
        }
        return new SchematicFile(result);
    }

    @Contract("_ -> new")
    public SchematicFile rotateY(int angle) {
        List<Pair<Vec3i, BlockData>> result = new ArrayList<>(blocks.size());
        for (Pair<Vec3i, BlockData> pair : blocks) {
            result.add(Pair.of(rotateY(pair.getLeft(), angle), pair.getRight()));
        }
        return new SchematicFile(result);
    }

    @Contract("_ -> new")
    public SchematicFile rotateX(int angle) {
        List<Pair<Vec3i, BlockData>> result = new ArrayList<>(blocks.size());
        for (Pair<Vec3i, BlockData> pair : blocks) {
            result.add(Pair.of(rotateX(pair.getLeft(), angle), pair.getRight()));
        }
        return new SchematicFile(result);
    }

    @Contract("_ -> new")
    public SchematicFile rotateZ(int angle) {
        List<Pair<Vec3i, BlockData>> result = new ArrayList<>(blocks.size());
        for (Pair<Vec3i, BlockData> pair : blocks) {
            result.add(Pair.of(rotateZ(pair.getLeft(), angle), pair.getRight()));
        }
        return new SchematicFile(result);
    }

    public Stream<Pair<Vec3i, BlockData>> stream() {
        return blocks.stream();
    }

    public List<Pair<Vec3i, BlockData>> blocks() {
        return blocks;
    }

    public SchematicFile copy() {
        return new SchematicFile(new ArrayList<>(blocks));
    }

    private Vec3i rotateY(Vec3i point, int angle) {
        int x = point.x();
        int y = point.y();
        int z = point.z();
        return switch (angle) {
            case 0, 360 -> point;
            case 90 -> new Vec3i(-z, y, x);
            case 180 -> new Vec3i(-x, y, -z);
            case 270 -> new Vec3i(z, y, -x);
            default -> throw new IllegalArgumentException("Unsupported angle: " + angle);
        };
    }

    private Vec3i rotateX(Vec3i point, int angle) {
        int x = point.x();
        int y = point.y();
        int z = point.z();
        return switch (angle) {
            case 0, 360 -> point;
            case 90 -> new Vec3i(x, -z, y);
            case 180 -> new Vec3i(x, -y, -z);
            case 270 -> new Vec3i(x, z, -y);
            default -> throw new IllegalArgumentException("Unsupported angle: " + angle);
        };
    }

    private Vec3i rotateZ(Vec3i point, int angle) {
        int x = point.x();
        int y = point.y();
        int z = point.z();
        return switch (angle) {
            case 0, 360 -> point;
            case 90 -> new Vec3i(-y, x, z);
            case 180 -> new Vec3i(-x, -y, z);
            case 270 -> new Vec3i(y, -x, z);
            default -> throw new IllegalArgumentException("Unsupported angle: " + angle);
        };
    }

    public static List<Pair<Vec3i, BlockData>> readFromFile(String schem) {
        if (BAF.RUNNING_IN_IDE) return new ArrayList<>();
        try {
            File pluginsFolder = new File("./plugins");

            File shemFile = null;
            if (schem.startsWith("../")) {
                shemFile = new File(pluginsFolder, schem.substring(3));
            } else {
                List<String> plugins = List.of("BAirdropFlow", "WorldEdit", "FastAsyncWorldEdit");
                for (String s : plugins) {
                    File f = new File(pluginsFolder, s + "/schematics" + schem);
                    if (f.exists()) {
                        shemFile = f;
                        break;
                    }
                }
            }
            if (shemFile == null || !shemFile.exists())
                throw new FileNotFoundException(schem);
            try (var in = new FileInputStream(shemFile)) {
                return read(
                        in,
                        Objects.requireNonNullElse(ClipboardFormats.findByFile(shemFile), BuiltInClipboardFormat.SPONGE_SCHEMATIC)
                );
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException("FileNotFoundException: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read schematic file: " + e.getMessage(), e);
        }
    }

    public static List<Pair<Vec3i, BlockData>> readFromString(String schem) {
        return read(new ByteArrayInputStream(Base64.getDecoder().decode(schem.getBytes(StandardCharsets.UTF_8))), BuiltInClipboardFormat.SPONGE_SCHEMATIC);
    }

    private static List<Pair<Vec3i, BlockData>> read(final InputStream in, ClipboardFormat format) {
        List<Pair<Vec3i, BlockData>> blocks = new ArrayList<>();
        try (in; ClipboardReader reader = format.getReader(in)) {
            Clipboard clipboard = reader.read();
            Vec3i minPoint = toVec3i(clipboard.getMinimumPoint());
            Vec3i maxPoint = toVec3i(clipboard.getMaximumPoint());
            Vec3i origin = toVec3i(clipboard.getOrigin());

            for (int y = minPoint.y(); y <= maxPoint.y(); y++) {
                for (int x = minPoint.x(); x <= maxPoint.x(); x++) {
                    for (int z = minPoint.z(); z <= maxPoint.z(); z++) {
                        BlockState block = clipboard.getBlock(BlockVector3.at(x, y, z));

                        Vec3i pos = new Vec3i(x, y, z).sub(origin);

                        blocks.add(Pair.of(pos, BukkitAdapter.adapt(block)));
                    }
                }
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to read shem", e);
        }
        return blocks;
    }

    private static Vec3i toVec3i(BlockVector3 vec) {
        return new Vec3i(vec.getX(), vec.getY(), vec.getZ());
    }
}
