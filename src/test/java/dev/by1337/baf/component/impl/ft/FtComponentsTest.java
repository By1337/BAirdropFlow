package dev.by1337.baf.component.impl.ft;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.AirdropManager;
import dev.by1337.baf.component.AirdropComponents;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.YamlMap;
import dev.by1337.yaml.YamlValue;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FtComponentsTest {
    private World world;
    private Airdrop air;
    private EventContext ctx;
    private final List<Entity> spawned = new ArrayList<>();

    @BeforeAll
    static void init() {
        try (var ignored = new EventContext(false)) {
        }
    }

    @BeforeEach
    void setup() {
        MockBukkit.mock();
        world = mock(World.class);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        var manager = mock(AirdropManager.class);
        when(manager.plugin()).thenReturn(MockBukkit.createMockPlugin());
        air = new Airdrop(null, manager, new Location(world, 0, 64, 0));
        ctx = new EventContext(false, null, Airdrop.PLACEHOLDERS.bindCtx(air));
        doAnswer(call -> {
            Class<? extends Entity> type = call.getArgument(1);
            Entity entity = mock(type);
            AtomicReference<Location> location = new AtomicReference<>(((Location) call.getArgument(0)).clone());
            when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
            when(entity.getWorld()).thenReturn(world);
            when(entity.getLocation()).thenAnswer(c -> location.get().clone());
            when(entity.isValid()).thenReturn(true);
            when(entity.teleport(any(Location.class))).thenAnswer(c -> {
                location.set(((Location) c.getArgument(0)).clone());
                return true;
            });
            doAnswer(c -> {
                when(entity.isValid()).thenReturn(false);
                return null;
            }).when(entity).remove();
            if (entity instanceof LivingEntity living) {
                var health = mock(org.bukkit.attribute.AttributeInstance.class);
                when(health.getBaseValue()).thenReturn(20D);
                when(health.getValue()).thenReturn(100D);
                when(living.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)).thenReturn(health);
                when(living.getEquipment()).thenReturn(mock(EntityEquipment.class));
            }
            Consumer<Entity> callback = call.getArgument(2);
            callback.accept(entity);
            spawned.add(entity);
            return entity;
        }).when(world).spawn(any(Location.class), any(Class.class), org.mockito.ArgumentMatchers.<Consumer<Entity>>any());
    }

    @AfterEach
    void cleanup() {
        air.cleaner().cleanup();
        ctx.close();
        MockBukkit.unmock();
    }

    private FtComponent create(dev.by1337.baf.component.ComponentFactory config) {
        var component = (FtComponent) config.create(air, "ft");
        air.components().add(component, ctx);
        return component;
    }

    private void tick(FtComponent component, int ticks) {
        for (int i = 0; i < ticks; i++) component.onSyncTick(ctx);
    }

    @Test
    void fallingTimelineWaitsAndGlobalCleanerRemovesEveryEntity() {
        var config = FallingBlocksComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("steps", List.of(
                Map.of("tick", 2, "material", "stone", "velocity", "0;1;0"), Map.of("tick", 5, "material", "netherrack"))))).getOrThrow();
        var component = create(config);
        tick(component, 2);
        assertEquals(0, spawned.size());
        tick(component, 1);
        assertEquals(1, spawned.size());
        var first = (FallingBlock) spawned.getFirst();
        verify(first).setPersistent(false);
        verify(first).setDropItem(false);
        verify(first).setHurtEntities(false);
        tick(component, 3);
        assertEquals(2, spawned.size());
        component.markRemoved(ctx);
        spawned.forEach(e -> verify(e).remove());
        tick(component, 20);
        assertEquals(2, spawned.size());
    }

    @Test
    void tntNeverExplodesAfterNodeRemovalAndInitializesFuseBeforeSpawn() {
        var component = create(TntComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("fuse_ticks", 100, "entity_lifetime_ticks", 200))).getOrThrow());
        var tnt = (TNTPrimed) spawned.getFirst();
        verify(tnt).setPersistent(false);
        verify(tnt).setFuseTicks(100);
        verify(tnt).setIsIncendiary(false);
        component.markRemoved(ctx);
        verify(tnt).remove();
        tick(component, 150);
        assertEquals(1, spawned.size());
    }

    @Test
    void emptyUnloadedTntFinishesWithoutLoadingAnything() {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        var component = create(TntComponent.Config.DECODER.decode(YamlValue.wrap(Map.of())).getOrThrow());
        tick(component, 1);
        assertTrue(component.isRemoved());
        assertTrue(spawned.isEmpty());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void mobWavesRemainNonPersistentAndUnloadingIsNotAKill() {
        var component = create(MobsComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("total", 2, "wave_size", 2,
                "max_alive", 2, "launch", false, "mob_name", ""))).getOrThrow());
        tick(component, 1);
        assertEquals(2, spawned.size());
        for (Entity entity : spawned) {
            verify(entity).setPersistent(false);
            var mob = (Mob) entity;
            verify(mob).setRemoveWhenFarAway(false);
            verify(mob).setCanPickupItems(false);
            verify(mob).setHealth(100D);
        }
        var unload = mock(org.bukkit.event.world.EntitiesUnloadEvent.class);
        when(unload.getEntities()).thenReturn(List.copyOf(spawned));
        component.entities.unload(unload);
        tick(component, 1);
        assertEquals(0, component.getPlaceholder("killed"));
        assertTrue(component.isRemoved());
        spawned.forEach(e -> verify(e).remove());
    }

    @Test
    void meteoriteMovesTheWholeModelAndCleanupRestoresItsTransientBlock() {
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(64);
        var blocks = new HashMap<String, org.bukkit.block.Block>();
        doAnswer(call -> {
            int x = call.getArgument(0), y = call.getArgument(1), z = call.getArgument(2);
            return blocks.computeIfAbsent(x + ":" + y + ":" + z, key -> {
                var block = mock(org.bukkit.block.Block.class);
                when(block.getLocation()).thenReturn(new Location(world, x, y, z));
                AtomicReference<org.bukkit.block.data.BlockData> data = new AtomicReference<>(Material.AIR.createBlockData());
                when(block.getBlockData()).thenAnswer(c -> data.get());
                doAnswer(c -> {
                    data.set(c.getArgument(0));
                    return null;
                }).when(block).setBlockData(any(org.bukkit.block.data.BlockData.class), eq(false));
                return block;
            });
        }).when(world).getBlockAt(anyInt(), anyInt(), anyInt());
        doAnswer(call -> {
            Location at = call.getArgument(0);
            return world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
        })
                .when(world).getBlockAt(any(Location.class));
        var component = create(MeteoriteComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("flight_ticks", 3, "wave_min", 1, "wave_max", 1))).getOrThrow());
        tick(component, 1);
        assertEquals(9, spawned.size());
        Entity first = spawned.getFirst();
        Location from = first.getLocation();
        tick(component, 1);
        assertEquals(from.getX() - 15D / 9, first.getLocation().getX(), 1e-6);
        assertEquals(from.getY() - 70D / 9, first.getLocation().getY(), 1e-6);
        tick(component, 2);
        assertEquals(17, spawned.size());
        verify(first).remove();
        assertTrue(blocks.values().stream().anyMatch(b -> b.getBlockData().getMaterial() == Material.BLACKSTONE));
        component.markRemoved(ctx);
        assertTrue(blocks.values().stream().allMatch(b -> b.getBlockData().getMaterial() == Material.AIR));
    }

    @Test
    void proceduralRaysExpandChangeMaterialsAndCleanUpWithoutResourceTable() {
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            var block = mock(org.bukkit.block.Block.class);
            when(block.getType()).thenReturn((int) call.getArgument(1) == 63 ? Material.STONE : Material.AIR);
            return block;
        });
        var decoded = StrangeBeaconComponent.Config.DECODER.decode(YamlValue.wrap(Map.of(
                "rays", Map.of("radius", 2, "count", 4, "step", 1, "speed", 1, "jitter", 0),
                "stages", List.of(Map.of("material", "stone", "ticks", 1), Map.of("material", "gold_block", "ticks", 1)),
                "build_start_tick", 4))).getOrThrow();
        var built = new java.util.concurrent.atomic.AtomicInteger();
        var component = create(new StrangeBeaconComponent.Config(decoded.common(), decoded.rays(), decoded.ground(),
                decoded.stages(), decoded.buildStartTick(), c -> {
            built.incrementAndGet();
            return true;
        }));
        tick(component, 1);
        assertEquals(1, spawned.size());
        var center = (ArmorStand) spawned.getFirst();
        tick(component, 1);
        assertEquals(5, spawned.size());
        verify(center.getEquipment()).setHelmet(argThat(item -> item.getType() == Material.GOLD_BLOCK));
        tick(component, 1);
        assertEquals(9, spawned.size());
        verify(center).remove();
        for (int i = 5; i < 9; i++)
            assertEquals(2, Math.hypot(spawned.get(i).getLocation().getX(), spawned.get(i).getLocation().getZ()), 1e-6);
        tick(component, 2);
        assertEquals(1, built.get());
        assertTrue(component.isRemoved());
        spawned.forEach(e -> {
            verify(e).setPersistent(false);
            verify(e).remove();
        });
    }

    @Test
    void proceduralRaysNeverReadOrSpawnInUnloadedChunks() {
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        var component = create(StrangeBeaconComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("build_start_tick", 0))).getOrThrow());
        tick(component, 1);
        assertTrue(spawned.isEmpty());
        verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
        assertTrue(component.isRemoved());
    }

    @Test
    void rewardsSearchTheirOwnRadiusOnlyAtPayoutRegardlessOfVisibility() {
        Player center = mock(Player.class), edge = mock(Player.class), outside = mock(Player.class);
        for (Player p : List.of(center, edge, outside)) {
            when(p.isOnline()).thenReturn(true);
            when(p.getWorld()).thenReturn(world);
        }
        when(center.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(edge.getLocation()).thenReturn(new Location(world, 10, 64, 0));
        when(outside.getLocation()).thenReturn(new Location(world, 0, 75, 0));
        // The render cache can be empty, or include a player outside the reward sphere.
        when(world.getNearbyPlayers(any(Location.class), eq(10D))).thenReturn(List.of(center, edge, outside));
        assertTrue(air.nearbyPlayers().isEmpty());
        var decoded = ProximityRewardComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("radius", 10, "min_reward", 10,
                "max_reward", 90, "interval_ticks", 2))).getOrThrow();
        var paid = new HashMap<Player, String>();
        var component = create(new ProximityRewardComponent.Config(decoded.common(), decoded.interval(), decoded.radius(),
                decoded.min(), decoded.max(), c -> {
            paid.put(c.target(), c.setPlaceholders("{ctx:ft.amount}"));
            return true;
        }));
        tick(component, 1);
        assertEquals(Map.of(center, "45.00", edge, "5.00"), paid);
        verify(world, times(1)).getNearbyPlayers(any(Location.class), eq(10D));
        paid.clear();
        air.nearbyPlayers().add(outside);
        when(world.getNearbyPlayers(any(Location.class), eq(10D))).thenReturn(List.of(center, outside));
        tick(component, 1);
        assertTrue(paid.isEmpty());
        verify(world, times(1)).getNearbyPlayers(any(Location.class), eq(10D));
        tick(component, 1);
        assertEquals(Map.of(center, "90.00"), paid);
        verify(world, times(2)).getNearbyPlayers(any(Location.class), eq(10D));
        component.markRemoved(ctx);
        paid.clear();
        tick(component, 5);
        assertTrue(paid.isEmpty());
        verify(world, times(2)).getNearbyPlayers(any(Location.class), eq(10D));
    }

    @Test
    void allFtTypesAndTheShippedConfigsDecode() {
        AirdropComponents.boot();
        for (String key : List.of("falling_blocks", "tnt", "firework", "mobs", "meteorites", "geyser", "strange_beacon", "items", "beacon_pulse", "proximity_reward")) {
            Map<String, Object> values = new HashMap<>();
            values.put("type", "ft:" + key);
            if (key.equals("items")) values.put("loot_table", "test");
            assertDoesNotThrow(() -> dev.by1337.baf.component.AirdropComponent.REGISTRY.decoder.decode(YamlValue.wrap(values)).getOrThrow(), key);
        }
        java.io.File examples = new java.io.File("CONFIGS/ft-examples.yml");
        if (!examples.isFile()) return; // Local migration artifacts are optional in a source-only checkout.
        for (var entry : YamlMap.load(examples).getRaw().entrySet()) {
            assertDoesNotThrow(() -> dev.by1337.baf.component.AirdropComponent.REGISTRY.decoder.decode(YamlValue.wrap(entry.getValue())).getOrThrow(), entry.getKey());
        }
    }

    @Test
    void webDefaultsProduceValidFtConfigurations() {
        AirdropComponents.boot();
        for (var entry : dev.by1337.baf.component.AirdropComponent.REGISTRY.schemas().entrySet()) {
            if (!entry.getKey().startsWith("ft:")) continue;
            var defaults = new com.google.gson.JsonObject();
            defaults.addProperty("type", entry.getKey());
            entry.getValue().getAsJsonObject("properties").entrySet().forEach(field -> {
                var value = field.getValue().getAsJsonObject();
                if (value.has("default")) defaults.add(field.getKey(), value.get("default"));
            });
            var result = dev.by1337.baf.component.AirdropComponent.REGISTRY.decoder.decode(YamlMap.loadFromString(defaults.toString()).get());
            assertFalse(result.hasError(), entry.getKey() + ": " + result.error());
        }
    }

    @Test
    void invalidLimitsAndTimingsAreRejected() {
        for (var values : List.of(Map.of("rays", Map.of("step", 0)), Map.of("rays", Map.of("speed", 0)),
                Map.of("stages", List.of()), Map.of("build_start_tick", -1)))
            assertTrue(StrangeBeaconComponent.Config.DECODER.decode(YamlValue.wrap(values)).hasError());
        for (var values : List.of(Map.of("entity_lifetime_ticks", 0), Map.of("max_entities", 0), Map.of("ticks", 0), Map.of("fuse_ticks", -1)))
            assertTrue(TntComponent.Config.DECODER.decode(YamlValue.wrap(values)).hasError());
        assertTrue(MobsComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("wave_size", 0))).hasError());
        assertTrue(FallingBlocksComponent.Config.DECODER.decode(YamlValue.wrap(Map.of("steps", List.of(Map.of("tick", -1))))).hasError());
    }
}
