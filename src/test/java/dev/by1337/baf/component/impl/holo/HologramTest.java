package dev.by1337.baf.component.impl.holo;

import dev.by1337.core.BCore;
import dev.by1337.core.entity.EntityWrapper;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HologramTest {
    @Test
    void linesKeepSpacingAndOnlyChangedNamesAreUpdated() {
        World world = mock(World.class);
        EntityWrapper.Maker maker = mock(EntityWrapper.Maker.class);
        List<EntityWrapper> wrappers = new ArrayList<>();
        List<ArmorStand> stands = new ArrayList<>();
        when(maker.make(eq(EntityType.ARMOR_STAND), eq(world), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    EntityWrapper wrapper = mock(EntityWrapper.class);
                    ArmorStand stand = mock(ArmorStand.class);
                    when(wrapper.asBukkit()).thenReturn(stand);
                    wrappers.add(wrapper);
                    stands.add(stand);
                    return wrapper;
                });
        try (var core = mockStatic(BCore.class)) {
            core.when(BCore::getEntityWrapperMaker).thenReturn(maker);
            Hologram hologram = new Hologram(world, 1, 65, 2);
            hologram.setLines(List.of("first", "second", "third"));
            verify(maker).make(EntityType.ARMOR_STAND, world, 1, 65, 2);
            verify(maker).make(EntityType.ARMOR_STAND, world, 1, 64.7, 2);
            verify(maker).make(EntityType.ARMOR_STAND, world, 1, 64.4, 2);

            Player player = player(world);
            hologram.sync(Set.of(player));
            wrappers.forEach(wrapper -> verify(wrapper).sendSpawnPackets(player));
            stands.forEach(stand -> clearInvocations(stand));
            hologram.setLines(List.of(new String("first"), "changed", "third"));
            verify(stands.get(0), never()).customName(any(Component.class));
            verify(stands.get(1)).customName(Component.text("changed"));
            verify(stands.get(2), never()).customName(any(Component.class));

            hologram.setLines(List.of("first"));
            verify(wrappers.get(1)).sendRemovePacket(player);
            verify(wrappers.get(2)).sendRemovePacket(player);
            hologram.setLines(List.of("first", "new"));
            hologram.sync(Set.of(player));
            verify(wrappers.get(3)).sendSpawnPackets(player);
            hologram.close();
            verify(wrappers.get(0)).sendRemovePacket(player);
            verify(wrappers.get(3)).sendRemovePacket(player);
        }
    }

    @Test
    void visibilityAndMetadataFollowViewersAndCleanup() {
        World world = mock(World.class);
        EntityWrapper.Maker maker = mock(EntityWrapper.Maker.class);
        EntityWrapper wrapper = mock(EntityWrapper.class);
        when(wrapper.asBukkit()).thenReturn(mock(ArmorStand.class));
        when(wrapper.hasDirtyData()).thenReturn(true);
        when(maker.make(any(), any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(wrapper);
        try (var core = mockStatic(BCore.class)) {
            core.when(BCore::getEntityWrapperMaker).thenReturn(maker);
            Hologram hologram = new Hologram(world, 0, 0, 0);
            hologram.setLines(List.of("hello"));
            Player first = player(world), second = player(world);
            hologram.sync(Set.of(first, second));
            var order = inOrder(wrapper);
            order.verify(wrapper).sendDirtyData(any());
            order.verify(wrapper).sendDirtyData(any());
            order.verify(wrapper).removeDirtyData();
            hologram.sync(Set.of(second));
            verify(wrapper).sendRemovePacket(first);
            verify(wrapper, times(1)).sendSpawnPackets(second);
            hologram.sync(Set.of(first, second));
            verify(wrapper, times(2)).sendSpawnPackets(first);
            hologram.close();
            verify(wrapper, times(2)).sendRemovePacket(first);
            verify(wrapper).sendRemovePacket(second);
            hologram.close();
            verify(wrapper, times(2)).sendRemovePacket(first);
        }
    }

    private Player player(World world) {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }
}
