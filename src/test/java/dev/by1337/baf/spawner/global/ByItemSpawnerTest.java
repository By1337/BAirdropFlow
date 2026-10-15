package dev.by1337.baf.spawner.global;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.AirdropManager;
import dev.by1337.baf.Workflow;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
import dev.by1337.baf.workflow.WorkflowRepository;
import dev.by1337.item.registry.GlobalItemRegistry;
import dev.by1337.item.registry.ItemRegistry;
import dev.by1337.yaml.YamlValue;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ByItemSpawnerTest {
    @TempDir
    Path directory;
    ServerMock server;
    GlobalSpawners spawners;
    Workflow workflow;
    AirdropManager manager;
    Airdrop air;
    Block support;
    final Map<String, Block> blocks = new HashMap<>();
    final Map<String, Material> blockTypes = new HashMap<>();
    Material solid;

    private Block block(org.bukkit.World world, int x, int y, int z) {
        String key = x + "," + y + "," + z;
        if (blocks.containsKey(key)) return blocks.get(key);
        Block block = mock(Block.class);
        blocks.put(key, block);
        when(block.getWorld()).thenReturn(world);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        when(block.getLocation()).thenAnswer(ignored -> new Location(world, x, y, z));
        when(block.getRelative(anyInt(), anyInt(), anyInt())).thenAnswer(call ->
                block(world, x + call.<Integer>getArgument(0), y + call.<Integer>getArgument(1), z + call.<Integer>getArgument(2)));
        when(block.getType()).thenAnswer(ignored -> blockTypes.getOrDefault(key, Material.AIR) == Material.STONE ? solid : Material.AIR);
        when(block.isPassable()).thenAnswer(ignored -> blockTypes.getOrDefault(key, Material.AIR) != Material.STONE);
        doAnswer(call -> {
            blockTypes.put(key, call.getArgument(0));
            return null;
        }).when(block).setType(any(Material.class));
        return block;
    }

    @BeforeEach
    void setup() throws Exception {
        server = MockBukkit.mock();
        var world = server.addSimpleWorld("world");
        solid = mock(Material.class);
        when(solid.isSolid()).thenReturn(true);
        // MockBukkit does not implement Block#isPassable; model the block grid explicitly.
        support = block(world, 10, 64, 20);
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) {
                support.getRelative(x, 0, z).setType(Material.STONE);
                support.getRelative(x, 1, z).setType(Material.AIR);
                support.getRelative(x, 2, z).setType(Material.AIR);
            }
        workflow = mock(Workflow.class);
        air = mock(Airdrop.class);
        when(air.args()).thenReturn(new HashMap<>());
        when(workflow.make(any())).thenReturn(air);
        var launches = new dev.by1337.baf.launch.LaunchRegistry();
        launches.publish("workflow", "example:test", workflow);
        manager = mock(AirdropManager.class);
        AirdropSpawnerBoot.boot();
        spawners = new GlobalSpawners();
        spawners.load(directory, launches, manager, message -> fail(message));
        // ItemModel rendering uses Paper remapping and cannot run against MockBukkit's
        // Attribute interface with the installed ConfigurableItems 1.6 binary.
        spawners.items().register("test", new ByItemSpawner.SpawnItem(null, "example:test",
                Map.of("loot_count", "7"), new ByItemSpawner.Requirements(65, 65, true, 1, 2, false), true));
        spawners.enableItems(MockBukkit.createMockPlugin());
    }

    @AfterEach
    void cleanup() {
        if (spawners != null) spawners.close();
        MockBukkit.unmock();
    }

    private PlayerInteractEvent click(Block block, EquipmentSlot hand) {
        var player = server.addPlayer("Caller");
        assertSame(spawners.items(), GlobalItemRegistry.get("baf_airdrop"));
        // Build a registry-tagged fixture without invoking ItemModel rendering.
        var item = new ItemStack(Material.STONE);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(ItemRegistry.REGISTRY_KEY, PersistentDataType.STRING, "baf_airdrop");
            meta.getPersistentDataContainer().set(NamespacedKey.fromString("baf_airdrop:type"), PersistentDataType.STRING, "test");
        });
        item.setAmount(2);
        player.getInventory().setItemInMainHand(item);
        return new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, BlockFace.UP, hand);
    }

    //  @Test // TODO PAPI
    void createsImmediatelyWithArgumentsAndConsumesOneRegisteredItem() {
        var event = click(support, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        verify(workflow).make(new Location(support.getWorld(), 10, 65, 20));
        verify(workflow, never()).createSpawner();
        verify(manager).addAirdrop(air);
        assertEquals("7", air.args().get("loot_count"));
        assertEquals("Caller", air.args().get("player"));
        assertEquals(event.getPlayer().getUniqueId().toString(), air.args().get("player_uuid"));
        assertEquals(1, event.getPlayer().getInventory().getItemInMainHand().getAmount());
    }

    @Test
    void failedSurfaceDoesNotConsumeOrSpawn() {
        support.getRelative(1, 0, 1).setType(Material.AIR);
        var event = click(support, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertEquals(2, event.getItem().getAmount());
        verify(workflow, never()).make(any());
        verify(manager, never()).addAirdrop(any());
    }

    @Test
    void ignoresOffhandAndPreviouslyCancelledEvents() {
        var offhand = click(support, EquipmentSlot.OFF_HAND);
        server.getPluginManager().callEvent(offhand);
        var cancelled = new PlayerInteractEvent(offhand.getPlayer(), Action.RIGHT_CLICK_BLOCK,
                offhand.getItem(), support, BlockFace.UP, EquipmentSlot.HAND);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        verify(workflow, never()).make(any());
        assertEquals(2, offhand.getItem().getAmount());
    }

    @Test
    void validatesHeightClearanceAndRequirementBounds() {
        var rules = ByItemSpawner.Requirements.DECODER.decode(YamlValue.wrap(Map.of(
                "min_y", 65, "max_y", 65, "flat_surface", true, "radius", 1, "clear_height", 2))).getOrThrow();
        assertNull(rules.failure(support));
        assertEquals("height", rules.failure(support.getRelative(0, 1, 0)));
        support.getRelative(0, 2, 0).setType(Material.STONE);
        assertEquals("surface", rules.failure(support));
        assertThrows(IllegalArgumentException.class, () -> new ByItemSpawner.Requirements(5, 4, false, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> new ByItemSpawner.Requirements(0, 100, false, 33, 1, false));
    }

    @Test
    void regionRequirementRejectsWhenWorldGuardIsUnavailable() {
        var rules = new ByItemSpawner.Requirements(65, 65, true, 1, 2, true);
        assertEquals("region", rules.failure(support));
    }

    @Test
    void rejectsUnknownWorkflowsBeforeStarting() {
        var registry = new ItemRegistry<ByItemSpawner.SpawnItem>(ByItemSpawner.SPACE);
        registry.register("missing", new ByItemSpawner.SpawnItem(null, "missing", Map.of(),
                ByItemSpawner.Requirements.DEFAULT, true));
        assertThrows(IllegalArgumentException.class,
                () -> new ByItemSpawner(registry).start(new SpawnerContext(new dev.by1337.baf.launch.LaunchRegistry(), null)));
    }

    @Test
    void closesRegistryAndListener() {
        var event = click(support, EquipmentSlot.HAND);
        spawners.close();
        assertNull(GlobalItemRegistry.get("baf_airdrop").byId("test"));
        server.getPluginManager().callEvent(event);
        verify(workflow, never()).make(any());
    }
}
