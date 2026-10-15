package dev.by1337.baf.component.impl.holo;

import dev.by1337.core.BCore;
import dev.by1337.core.entity.EntityWrapper;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

final class WrappedArmorStand {
    private final World w;
    private final EntityWrapper entity;
    private final ViewerHolder viewers;
    private final ArmorStand armorStand;
    private Component last;

    public WrappedArmorStand(World w, double x, double y, double z, Component name) {
        viewers = new ViewerHolder();
        this.w = w;
        entity = BCore.getEntityWrapperMaker().make(EntityType.ARMOR_STAND, w, x, y, z);
        armorStand = (ArmorStand) entity.asBukkit();
        armorStand.setCustomNameVisible(true);
        armorStand.setSmall(true);
        armorStand.setGravity(false);
        armorStand.setArms(false);
        armorStand.setMarker(true);
        armorStand.setBasePlate(false);
        armorStand.setCanTick(false);
        armorStand.setInvulnerable(true);
        armorStand.setVisible(false);
        armorStand.setPersistent(false);
        armorStand.customName(last = name);
    }

    public void customName(Component name) {
        if (Objects.equals(name, last)) return;
        armorStand.customName(last = name);
    }

    public void show(Player player) {
        if (!player.isOnline()) return;
        if (player.getWorld() != w) return;
        if (viewers.contains(player)) return;
        entity.sendSpawnPackets(player);
        viewers.add(player);
    }

    public void sync(Player player) {
        if (!entity.hasDirtyData()) return;
        if (!player.isOnline()) return;
        if (player.getWorld() != w) return;
        if (!viewers.contains(player)) return;
        entity.sendDirtyData(player);
    }

    public void hide(Player player) {
        if (!viewers.remove(player)) return;
        if (!player.isOnline()) return;
        if (player.getWorld() != w) return;
        entity.sendRemovePacket(player);
    }

    public void close() {
        viewers.forEach(player -> {
            if (!player.isOnline()) return;
            if (player.getWorld() != w) return;
            entity.sendRemovePacket(player);
        });
        viewers.clear();
    }

    public void pop() {
        entity.removeDirtyData();
    }

    private static class ViewerHolder {
        private final Object2ObjectOpenHashMap<UUID, Player> viewers = new Object2ObjectOpenHashMap<>();

        public boolean contains(Player o) {
            return viewers.containsKey(o.getUniqueId());
        }

        public void add(Player element) {
            viewers.put(element.getUniqueId(), element);
        }

        public boolean remove(Player o) {
            return viewers.remove(o.getUniqueId()) != null;
        }

        public void clear() {
            viewers.clear();
        }

        public void forEach(Consumer<Player> c) {
            viewers.values().forEach(c::accept);
        }
    }
}
