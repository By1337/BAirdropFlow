package dev.by1337.baf.generator.impl;

import dev.by1337.baf.BAF;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.baf.world.*;
import dev.by1337.yaml.YamlMap;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RandomGeneratorTest {
    @Test void samplesBothSidesOfConfiguredCenterAndUsesExistingAsyncChunkCollection() {
        MockBukkit.mock();
        try {
            var generator = new RandomGenerator(1, 50, 70, 2, 3, m -> true, m -> true,
                    new Vec3i(0, 2, 0), new Vec3i(100, 0, -200));
            var sampled = new HashSet<Vec3i>();
            for (int i = 0; i < 1000; i++) {
                var point = generator.randomPoint();
                assertTrue(point.x() >= 99 && point.x() <= 101); assertTrue(point.z() >= -201 && point.z() <= -199);
                sampled.add(point);
            }
            assertEquals(9, sampled.size());
            World world = mock(World.class); WorldData data = mock(WorldData.class);
            @SuppressWarnings("unchecked") WorldDataContainer<WorldData> worlds = mock(WorldDataContainer.class);
            when(worlds.getIn(world)).thenReturn(data);
            when(data.collectChunks(anyInt(), anyInt(), anyInt(), anyInt()))
                    .thenReturn(CompletableFuture.completedFuture(new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>()));
            doAnswer(call -> {
                int y = call.getArgument(0); WorldData.LayerResult layer = call.getArgument(5);
                layer.airs = y >= 64 ? 100 : 0; layer.solid = y < 64 ? 100 : 0; return null;
            }).when(data).countOfBlocks(anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), any(), any());
            try (var baf = mockStatic(BAF.class)) {
                baf.when(BAF::worldData).thenReturn(worlds);
                var at = generator.tryGenerate(world).join();
                assertNotNull(at); assertEquals(66, at.getBlockY());
                assertTrue(at.getBlockX() >= 99 && at.getBlockX() <= 101);
                assertTrue(at.getBlockZ() >= -201 && at.getBlockZ() <= -199);
                verifyNoInteractions(world);
            }
            for (String bad : new String[]{"radius: 0", "clear_zone: 0", "min_y: 100\nmax_y: 50"})
                assertTrue(RandomGenerator.DECODER.decode(YamlMap.loadFromString(bad).get()).hasError());
            assertFalse(RandomGenerator.DECODER.decode(YamlMap.loadFromString("center: '10;0;-20'").get()).hasError());
        } finally { MockBukkit.unmock(); }
    }
}
