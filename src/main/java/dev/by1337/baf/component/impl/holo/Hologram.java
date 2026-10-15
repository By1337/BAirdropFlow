package dev.by1337.baf.component.impl.holo;

import dev.by1337.core.util.text.minimessage.MiniMessage;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.*;

final class Hologram {
    private static final double LINE_SPACING = 0.3;
    private final List<Line> entities = new ArrayList<>();
    private final Set<Player> viewers = new HashSet<>();
    private final World world;
    private final double x;
    private final double y;
    private final double z;

    public Hologram(World world, double x, double y, double z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setLines(List<String> lines) {
        while (entities.size() > lines.size()) {
            entities.removeLast().entity.close();
        }
        for (int i = 0; i < lines.size(); i++) {
            String text = lines.get(i);
            if (i < entities.size()) {
                Line line = entities.get(i);
                if (!Objects.equals(line.text, text)) {
                    line.entity.customName(MiniMessage.deserialize(text));
                    entities.set(i, new Line(text, line.entity));
                }
            } else {
                entities.add(new Line(text, new WrappedArmorStand(world, x, y - i * LINE_SPACING, z,
                        MiniMessage.deserialize(text))));
            }
        }
    }

    public void sync(Set<Player> nearby) {
        var iterator = viewers.iterator();
        while (iterator.hasNext()) {
            Player player = iterator.next();
            if (!nearby.contains(player) || !player.isOnline() || player.getWorld() != world) {
                entities.forEach(line -> line.entity.hide(player));
                iterator.remove();
            }
        }
        for (Player player : nearby) {
            if (!player.isOnline() || player.getWorld() != world) continue;
            viewers.add(player);
            for (Line line : entities) {
                line.entity.show(player);
                line.entity.sync(player);
            }
        }
        // EntityWrapper caches dirty metadata until every viewer has received it.
        entities.forEach(line -> line.entity.pop());
    }

    private record Line(String text, WrappedArmorStand entity) {
    }

    public void close() {
        entities.forEach(line -> line.entity.close());
        entities.clear();
        viewers.clear();
    }
}
