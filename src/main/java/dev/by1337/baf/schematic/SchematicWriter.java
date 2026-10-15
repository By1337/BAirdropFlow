package dev.by1337.baf.schematic;

import dev.by1337.baf.BAF;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.baf.world.fake.FakeBlockSetter;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.bukkit.World;

public class SchematicWriter {
    private final LongOpenHashSet placedBlocks = new LongOpenHashSet();
    private final World world;
    private final FakeBlockSetter fakeBlockSetter;
    private final Vec3i at;

    public SchematicWriter(World world, Vec3i at) {
        this.world = world;
        fakeBlockSetter = BAF.fakeBlocks().getIn(world);
        this.at = at;
    }

    public void writeSchematic(Schematic schematic) {
        schematic.schematicFile().stream().forEach(pair -> writeBlock(schematic, pair.getKey(), pair.getRight()));
    }

    public boolean writeBlock(Schematic schematic, Vec3i position, org.bukkit.block.data.BlockData data) {
        if (schematic.no_place().contains(data.getMaterial())) return false;
        int x = position.x() + at.x(), y = position.y() + at.y(), z = position.z() + at.z();
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return false;
        if (schematic.no_replace().contains(world.getBlockAt(x, y, z).getType())) return false;
        placedBlocks.add(Vec3i.asLong(x, y, z));
        fakeBlockSetter.set(x, y, z, schematic.replace(data));
        return true;
    }

    public void undoSchematics() {
        var iter = placedBlocks.iterator();
        while (iter.hasNext()) {
            long pos = iter.nextLong();
            fakeBlockSetter.undo(
                    Vec3i.getX(pos),
                    Vec3i.getY(pos),
                    Vec3i.getZ(pos)
            );
        }
    }
}
