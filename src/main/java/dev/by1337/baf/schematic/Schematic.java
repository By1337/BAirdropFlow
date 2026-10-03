package dev.by1337.baf.schematic;

import dev.by1337.baf.BAF;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.core.util.misc.Pair;
import dev.by1337.yaml.decoder.BukkitYamlDecoders;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Material;
import org.bukkit.block.data.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

public class Schematic {
    private static final Random random = new Random();
    public static final YamlDecoder<Schematic> DECODER = RecordYamlDecoder.mapOf(
            Schematic::new,
            SchematicFile.DECODER.fieldOf("path"),
            Vec3i.DECODER.fieldOf("offsets", Vec3i.ZERO),
            BukkitYamlDecoders.material().wildcard().asSet().fieldOf("no_replace"),
            BukkitYamlDecoders.material().wildcard().asSet().fieldOf("no_place"),
            YamlDecoder.flattenKeys(
                    BukkitYamlDecoders.material().wildcard(),
                    BukkitYamlDecoders.material().map(v -> v.createBlockData()),
                    (v, v1) -> v
            ).fieldOf("replace", Map.of())
    );

    private boolean auto_rotate = false;
    private final SchematicFile schematicFile;
    private final Vec3i offsets;
    private final Set<Material> no_replace;
    private final Set<Material> no_place;
    private final Map<Material, BlockData> replaces;

    public Schematic(SchematicFile schematicFile, Vec3i offsets, Set<Material> noReplace, Set<Material> noPlace, Map<Material, BlockData> replaces) {
        this.schematicFile = offsets.isZero() ? schematicFile : schematicFile.mutatePos(v -> v.add(offsets));
        this.offsets = offsets;
        no_replace = noReplace;
        no_place = noPlace;
        this.replaces = replaces;
    }


    private SchematicFile mutateOrCopy(SchematicFile o, Function<SchematicFile, SchematicFile> mutator) {
        if (auto_rotate) return mutator.apply(o);
        return o.copy();
    }
    /*private Stream<Pair<Vec3i, BlockData>> bloksStream(SchematicFile s) {
        return s.stream().map(this::mutate);
    }
    private Pair<Vec3i, BlockData> mutate(Pair<Vec3i, BlockData> pair) {
        BlockData blockData = pair.getRight();
        var list = replaces.get(blockData.getMaterial());
        if (list == null) return pair;
        BlockData newData = list.get(random.nextInt(list.size())).createBlockData();
        if (blockData instanceof Ageable v1 && newData instanceof Ageable v2) {
            v2.setAge(v1.getAge());
        }
        if (blockData instanceof AnaloguePowerable v1 && newData instanceof AnaloguePowerable v2) {
            v2.setPower(v1.getPower());
        }
        if (blockData instanceof Attachable v1 && newData instanceof Attachable v2) {
            v2.setAttached(v1.isAttached());
        }
        if (blockData instanceof Bisected v1 && newData instanceof Bisected v2) {
            v2.setHalf(v1.getHalf());
        }
        if (blockData instanceof Directional v1 && newData instanceof Directional v2) {
            v2.setFacing(v1.getFacing());
        }
        if (blockData instanceof FaceAttachable v1 && newData instanceof FaceAttachable v2) {
            v2.setAttachedFace(v1.getAttachedFace());
        }
        if (blockData instanceof Levelled v1 && newData instanceof Levelled v2) {
            v2.setLevel(v1.getLevel());
        }
        if (blockData instanceof Lightable v1 && newData instanceof Lightable v2) {
            v2.setLit(v1.isLit());
        }
        if (blockData instanceof Lightable v1 && newData instanceof Lightable v2) {
            v2.setLit(v1.isLit());
        }
        if (blockData instanceof Openable v1 && newData instanceof Openable v2) {
            v2.setOpen(v1.isOpen());
        }
        if (blockData instanceof Orientable v1 && newData instanceof Orientable v2) {
            v2.setAxis(v1.getAxis());
        }
        if (blockData instanceof Powerable v1 && newData instanceof Powerable v2) {
            v2.setPowered(v1.isPowered());
        }
        if (blockData instanceof Rail v1 && newData instanceof Rail v2) {
            v2.setShape(v1.getShape());
        }
        if (blockData instanceof Rotatable v1 && newData instanceof Rotatable v2) {
            v2.setRotation(v1.getRotation());
        }
        if (blockData instanceof Snowable v1 && newData instanceof Snowable v2) {
            v2.setSnowy(v1.isSnowy());
        }
        if (blockData instanceof Waterlogged v1 && newData instanceof Waterlogged v2) {
            v2.setWaterlogged(v1.isWaterlogged());
        }
        return Pair.of(pair.getLeft(), newData);
    }*/
    public boolean canReplace(Material material){
        return !no_replace.contains(material);
    }

    public SchematicFile schematicFile() {
        return schematicFile;
    }

    public Set<Material> no_replace() {
        return no_replace;
    }

    public Set<Material> no_place() {
        return no_place;
    }

    public BlockData replace(BlockData m){
        return replaces.getOrDefault(m.getMaterial(), m);
    }

    public Map<Material, BlockData> replaces() {
        return replaces;
    }

    private static <T> T any(T t, T t1, T t2, T t3, T t4) {
        return any(any(t, t1, t2, t3), t4);
    }
    private static <T> T any(T t, T t1, T t2, T t3) {
        return any(any(t, t1), any(t2, t3));
    }

    private static <T> T any(T t, T t1, T t2) {
        return any(t, any(t1, t2));
    }

    private static <T> T any(T t, T t1) {
        return t != null ? t : t1;
    }
}
