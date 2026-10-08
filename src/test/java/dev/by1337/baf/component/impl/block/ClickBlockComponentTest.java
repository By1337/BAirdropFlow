package dev.by1337.baf.component.impl.block;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.BAF;
import dev.by1337.baf.interact.InteractManager;
import dev.by1337.baf.util.Vec3i;
import dev.by1337.edsl.context.EventContext;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class ClickBlockComponentTest {
    private ServerMock server;
    private Airdrop airdrop;
    private Player player;
    private Block block;
    private InteractManager interactions;
    private EventContext context;
    private MockedStatic<BAF> baf;
    private final List<String> outputs = new ArrayList<>();

    @BeforeAll
    static void initializeContext() {
        try (var ignored = new EventContext(false)) {
        }
    }

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        var world = server.addSimpleWorld("world");
        airdrop = new Airdrop(null, null, new Location(world, 10, 64, 20));
        player = server.addPlayer();
        block = world.getBlockAt(11, 66, 23);
        interactions = new InteractManager(MockBukkit.createMockPlugin());
        interactions.enable();
        context = new EventContext(false, null, Airdrop.PLACEHOLDERS.bindCtx(airdrop));
        baf = mockStatic(BAF.class);
        baf.when(BAF::blockInteract).thenReturn(interactions);
        baf.when(() -> BAF.newContext(airdrop, player)).thenAnswer(ignored ->
                new EventContext(false, player, Airdrop.PLACEHOLDERS.bindCtx(airdrop)));
    }

    @AfterEach
    void cleanup() {
        baf.close();
        context.close();
        interactions.disable();
        MockBukkit.unmock();
    }

    private ClickBlockComponent create(int count) {
        var config = new ClickBlockComponent.Config(new Vec3i(1, 2, 3), count,
                ctx -> outputs.add("create"),
                ctx -> {
                    assertSame(player, ctx.source());
                    return outputs.add("click");
                },
                ctx -> outputs.add("remove"),
                ctx -> {
                    assertSame(player, ctx.source());
                    return outputs.add("clicked");
                });
        var component = (ClickBlockComponent) config.create(airdrop, "click");
        airdrop.components().add(component, context);
        return component;
    }

    private PlayerInteractEvent click(Block target, Action action, EquipmentSlot hand) {
        var event = new PlayerInteractEvent(player, action, null, target, BlockFace.UP, hand);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void resolvesOffsetsAndCountFromAirdropArgumentsBeforeRegisteringTheBlock() {
        airdrop.args().put("offsets", "1;2;3");
        airdrop.args().put("clicks", "2");
        var config = ClickBlockComponent.Config.DECODER.decode(dev.by1337.yaml.YamlValue.wrap(java.util.Map.of(
                "offsets", "{args:offsets}", "count", "{args:clicks}"))).getOrThrow();
        var component = (ClickBlockComponent) config.create(airdrop, "dynamic");
        try (var unbound = new EventContext(false)) {
            airdrop.components().add(component, unbound);
        }
        assertEquals(2, component.getPlaceholder("count"));
        click(block, Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND);
        assertEquals(1, component.getPlaceholder("count"));
        click(block, Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND);
        assertTrue(component.isRemoved());
    }

    @Test
    void countsMainHandClicksAtOffsetAndCompletesOnce() {
        var component = create(2);
        assertEquals(2, component.getPlaceholder("count"));
        click(block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND);
        click(block, Action.PHYSICAL, EquipmentSlot.HAND);
        click(airdrop.world().getBlockAt(10, 64, 20), Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        assertEquals(2, component.getPlaceholder("count"));
        var first = click(block, Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND);
        assertEquals(org.bukkit.event.Event.Result.DENY, first.useInteractedBlock());
        assertEquals(1, component.getPlaceholder("count"));
        click(block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        assertEquals(List.of("create", "click", "clicked", "remove"), outputs);
        assertTrue(component.isRemoved());
        assertNull(airdrop.components().get("click"));
        click(block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        assertEquals(0, component.getPlaceholder("count"));
        assertEquals(4, outputs.size());
    }

    @Test
    void removalAndAirdropCleanupUnregisterClicks() {
        var component = create(3);
        component.markRemoved(context);
        click(block, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        assertEquals(3, component.getPlaceholder("count"));
        assertEquals(List.of("create", "remove"), outputs);
        var replacement = create(2);
        airdrop.cleaner().cleanup();
        click(block, Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND);
        assertEquals(2, replacement.getPlaceholder("count"));
        assertEquals(List.of("create", "remove", "create"), outputs);
    }
}
