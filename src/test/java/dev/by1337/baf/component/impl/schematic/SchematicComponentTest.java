package dev.by1337.baf.component.impl.schematic;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.schematic.Schematic;
import dev.by1337.baf.schematic.SchematicFile;
import dev.by1337.baf.schematic.SchematicWriter;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.core.util.misc.Pair;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.YamlValue;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchematicComponentTest {
    @BeforeAll
    static void initializeContext() {
        try (var ignored = new EventContext(false)) {
        }
    }

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void repeatedCreationReusesParsedSchematicAndOffsetsOnlyTheWriterPosition() {
        var world = MockBukkit.getMock().addSimpleWorld("world");
        var airdrop = new Airdrop(null, null, new Location(world, 10, 64, -20));
        airdrop.args().put("offset", "1;2;3");
        var positions = new ArrayList<Vec3i>();
        var schematics = new ArrayList<Schematic>();
        var localPosition = new Vec3i(4, 5, 6);
        var blocks = List.of(Pair.of(localPosition, Material.STONE.createBlockData()));
        try (var files = mockStatic(SchematicFile.class);
             var writers = mockConstruction(SchematicWriter.class, (writer, context) -> {
                 assertSame(world, context.arguments().get(0));
                 positions.add((Vec3i) context.arguments().get(1));
                 doAnswer(call -> {
                     schematics.add(call.getArgument(0));
                     return null;
                 })
                         .when(writer).writeSchematic(any(Schematic.class));
             });
             var ctx = new EventContext(false)) {
            files.when(() -> SchematicFile.readFromFile("fixed.schem")).thenReturn(blocks);
            var config = SchematicComponent.Config.DECODER.decode(YamlValue.wrap(Map.of(
                    "path", "fixed.schem", "offsets", "{args:offset}",
                    "no_replace", List.of("*diamond*"), "no_place", List.of("dirt"),
                    "replace", Map.of("stone", "gold_block")))).getOrThrow();
            files.verify(() -> SchematicFile.readFromFile("fixed.schem"), times(1));
            var component = config.create(airdrop, "schematic");
            component.onCreate(ctx);
            airdrop.args().put("offset", "-2;0;7");
            component.onCreate(ctx);

            assertEquals(List.of(new Vec3i(11, 66, -17), new Vec3i(8, 64, -13)), positions);
            assertEquals(2, schematics.size());
            assertSame(schematics.get(0), schematics.get(1));
            var schematic = schematics.get(0);
            assertEquals(localPosition, schematic.schematicFile().blocks().get(0).getLeft());
            assertTrue(schematic.no_replace().contains(Material.DIAMOND_BLOCK));
            assertEquals(Set.of(Material.DIRT), schematic.no_place());
            assertEquals(Material.GOLD_BLOCK, schematic.replace(blocks.get(0).getRight()).getMaterial());
            files.verify(() -> SchematicFile.readFromFile("fixed.schem"), times(1));
            verify(writers.constructed().get(0)).undoSchematics();
            component.onRemove(ctx);
            verify(writers.constructed().get(1)).undoSchematics();
        }
    }

    @Test
    void animatedPasteBuildsLayersOverTimeCompletesOnceAndCanBeCancelled() {
        var world = MockBukkit.getMock().addSimpleWorld("world");
        var airdrop = new Airdrop(null, null, new Location(world, 10, 64, -20));
        var blocks = List.of(Pair.of(new Vec3i(0, 1, 0), Material.BEACON.createBlockData()),
                Pair.of(new Vec3i(0, 0, 0), Material.GOLD_BLOCK.createBlockData()),
                Pair.of(new Vec3i(1, 0, 0), Material.GOLD_BLOCK.createBlockData()));
        var completed = new java.util.concurrent.atomic.AtomicInteger();
        var created = new java.util.concurrent.atomic.AtomicInteger();
        var order = new ArrayList<Vec3i>();
        try (var writers = mockConstruction(SchematicWriter.class, (writer, context) -> {
            when(writer.writeBlock(any(), any(), any())).thenAnswer(call -> {
                order.add(call.getArgument(1));
                return true;
            });
        }); var ctx = new EventContext(false)) {
            var schematic = new Schematic(new SchematicFile(blocks), Set.of(), Set.of(), Map.of());
            var config = new SchematicComponent.Config(schematic,
                    new dev.by1337.baf.config.value.Vec3iValue(Vec3i.ZERO), c -> {
                created.incrementAndGet();
                return true;
            },
                    dev.by1337.baf.component.ComponentFactory.EMPTY_HANDLER,
                    new SchematicComponent.Animation(2, 1, true, "", .8, 1.2), c -> {
                completed.incrementAndGet();
                return true;
            });
            var component = (SchematicComponent) config.create(airdrop, "structure");
            airdrop.components().add(component, ctx);
            assertEquals(1, created.get());
            assertTrue(order.isEmpty());
            for (int i = 0; i < 4; i++) component.onSyncTick(ctx);
            assertEquals(2, order.size());
            assertEquals(0, completed.get());
            assertTrue(order.stream().allMatch(p -> p.y() == 0));
            component.onSyncTick(ctx);
            assertEquals(1, order.getLast().y());
            assertEquals(1, completed.get());
            component.onSyncTick(ctx);
            assertEquals(1, completed.get());
            component.markRemoved(ctx);
            verify(writers.constructed().getFirst()).undoSchematics();
            order.clear();
            var cancelled = (SchematicComponent) config.create(airdrop, "cancelled");
            airdrop.components().add(cancelled, ctx);
            cancelled.onSyncTick(ctx);
            cancelled.markRemoved(ctx);
            for (int i = 0; i < 10; i++) cancelled.onSyncTick(ctx);
            assertEquals(1, order.size());
            assertEquals(1, completed.get());
            verify(writers.constructed().getLast()).undoSchematics();
        }
    }

    @Test
    void omittedOffsetsDefaultToTheAirdropPosition() {
        var world = MockBukkit.getMock().addSimpleWorld("world");
        var airdrop = new Airdrop(null, null, new Location(world, 10, 64, -20));
        try (var files = mockStatic(SchematicFile.class);
             var writers = mockConstruction(SchematicWriter.class, (writer, context) ->
                     assertEquals(new Vec3i(10, 64, -20), context.arguments().get(1)));
             var ctx = new EventContext(false)) {
            files.when(() -> SchematicFile.readFromFile("fixed.schem")).thenReturn(List.of());
            var config = SchematicComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("path", "fixed.schem"))).getOrThrow();
            var component = config.create(airdrop, "schematic");
            component.onCreate(ctx);
            assertEquals(1, writers.constructed().size());
            component.onRemove(ctx);
        }
    }
}
