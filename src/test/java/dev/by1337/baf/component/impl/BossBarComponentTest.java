package dev.by1337.baf.component.impl;

import dev.by1337.baf.Airdrop;
import dev.by1337.baf.component.ComponentFactory;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.YamlValue;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BossBarComponentTest {
    private ServerMock server;
    private WorldMock world;
    private Airdrop airdrop;
    private EventContext ctx;

    @BeforeAll
    static void initializeContextWithoutPlaceholderApi() {
        // These tests provide their own resolver; no PlaceholderAPI plugin is running in MockBukkit.
        try (var ignored = new EventContext(false)) {
        }
    }

    @BeforeEach
    void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        airdrop = new Airdrop(null, null, new Location(world, 0, 64, 0));
        ctx = new EventContext(false, null, Airdrop.PLACEHOLDERS.bindCtx(airdrop));
    }

    @AfterEach
    void cleanup() {
        if (ctx != null) ctx.close();
        MockBukkit.unmock();
    }

    private PlayerMock player(double x, double y, double z) {
        PlayerMock player = server.addPlayer();
        player.teleport(new Location(world, x, y, z));
        return player;
    }

    private BossBarComponent create(Map<String, Object> values) {
        var decoded = BossBarComponent.Config.DECODER.decode(YamlValue.wrap(values));
        assertFalse(decoded.hasError(), decoded.error());
        assertNotNull(decoded.result());
        var component = (BossBarComponent) decoded.result().create(airdrop, "bar");
        airdrop.components().add(component, ctx);
        return component;
    }

    @Test
    void visibilityFollowsRadiusAndWorldAndNodeRemovalHidesEveryViewer() {
        PlayerMock near = player(3, 64, 4), boundary = player(10, 64, 0), far = player(11, 64, 0);
        airdrop.tick(10*10);
        BossBarComponent component = create(Map.of("radius", 10, "progress", .4, "color", "red",
                "overlay", "notched_10", "flags", List.of("darken_screen", "play_boss_music", "create_world_fog")));
        assertEquals(1, near.getBossBars().size());
        assertEquals(1, boundary.getBossBars().size());
        assertTrue(far.getBossBars().isEmpty());
        BossBar bar = near.getBossBars().iterator().next();
        assertEquals(.4F, bar.progress());
        assertEquals(BossBar.Color.RED, bar.color());
        assertEquals(BossBar.Overlay.NOTCHED_10, bar.overlay());
        assertEquals(3, bar.flags().size());
        assertEquals("<lang:bairdropflow.time.format.seconds:0>", component.getPlaceholder("time"));

        near.teleport(new Location(world, 11, 64, 0));
        boundary.teleport(new Location(server.addSimpleWorld("other"), 0, 64, 0));
        far.teleport(new Location(world, 0, 64, 0));
        airdrop.tick(10*10);
        component.onSyncTick(ctx);
        assertTrue(near.getBossBars().isEmpty());
        assertTrue(boundary.getBossBars().isEmpty());
        assertEquals(java.util.Set.of(bar), far.getBossBars());

        near.teleport(new Location(world, 0, 64, 0));
        airdrop.tick(10*10);
        component.onSyncTick(ctx);
        assertEquals(1, near.getBossBars().size());
        component.markRemoved(ctx);
        assertTrue(near.getBossBars().isEmpty());
        assertTrue(far.getBossBars().isEmpty());
        assertNull(airdrop.components().get("bar"));
        airdrop.cleaner().cleanup(); // The released slot is also safe during global cleanup.
    }

    @Test
    void timeUsesInitialValueForProgressAndExportsFormattedSeconds() {
        PlayerMock player = player(0, 64, 0);
        var timer = (TimerComponent) new TimerComponent.Config(120, ComponentFactory.EMPTY_HANDLER,
                ComponentFactory.EMPTY_HANDLER, ComponentFactory.EMPTY_HANDLER, ComponentFactory.EMPTY_HANDLER).create(airdrop, "tto");
        airdrop.components().add(timer, ctx);
        airdrop.tick(10*10);
        BossBarComponent component = create(Map.of("time", "{ctx:tto.timer}", "progress_from_time", true,
                "title", "<gold>До открытия: <white>{ctx:bar.time}"));
        BossBar bar = player.getBossBars().iterator().next();
        assertEquals(1F, bar.progress());
        assertEquals("<lang:bairdropflow.time.format.minutes:2>", component.getPlaceholder("time"));
        for (int i = 0; i < 30; i++) timer.onTick(ctx);
        airdrop.tick(10*10);
        component.onSyncTick(ctx);
        assertEquals(.75F, bar.progress());
        assertEquals("<lang:bairdropflow.time.format.minutes.seconds:1:30>", component.getPlaceholder("time"));
        assertNotEquals("<lang:bairdropflow.time.format.minutes:2>", ctx.setPlaceholders("{ctx:bar.time}"));
        assertEquals(.75F, bar.progress());
        airdrop.cleaner().cleanup();
        assertTrue(player.getBossBars().isEmpty());
        airdrop.tick(10*10);
        component.onSyncTick(ctx);
        assertTrue(player.getBossBars().isEmpty());
    }

    @Test
    void formattedTimeCanBeUsedWithStaticProgressAndZeroInitialTimeIsSafe() {
        PlayerMock player = player(0, 64, 0);
        airdrop.tick(10*10);
        BossBarComponent component = create(Map.of("time", "61", "progress", .25));
        assertEquals(.25F, player.getBossBars().iterator().next().progress());
        assertEquals("<lang:bairdropflow.time.format.minutes.seconds:1:1>", component.getPlaceholder("time"));
        component.markRemoved(ctx);
        component = create(Map.of("time", "0", "progress_from_time", true));
        assertEquals(0F, player.getBossBars().iterator().next().progress());
        component.markRemoved(ctx);
    }

    @Test
    void invalidRuntimeTimeRemovesTheNodeAndItsBossBar() {
        PlayerMock player = player(0, 64, 0);
        airdrop.tick(10*10);
        airdrop.args().put("seconds", "60");
        BossBarComponent component = create(Map.of("time", "{args:seconds}", "progress_from_time", true));
        assertFalse(player.getBossBars().isEmpty());
        airdrop.args().put("seconds", "NaN");
        component.onSyncTick(ctx);
        assertTrue(component.isRemoved());
        assertTrue(player.getBossBars().isEmpty());
        assertNull(airdrop.components().get("bar"));
        component = create(Map.of("time", "{ctx:missing.timer}"));
        assertTrue(component.isRemoved());
        assertTrue(player.getBossBars().isEmpty());
    }

    @Test
    void appearanceParametersResolveThroughAirdropAndUpdateWithItsArguments() {
        PlayerMock viewer = player(5, 64, 0);
        airdrop.args().putAll(Map.of("radius", "10", "progress", "0.5", "color", "red",
                "overlay", "progress", "flag", "darken_screen", "from_time", "false"));
        var settings = Map.<String, Object>of("radius", "{args:radius}", "progress", "{args:progress}",
                "color", "{args:color}", "overlay", "{args:overlay}", "flags", List.of("{args:flag}"),
                "progress_from_time", "{args:from_time}");
        var config = BossBarComponent.Config.DECODER.decode(YamlValue.wrap(settings)).getOrThrow();
        airdrop.tick(10*10);
        var component = (BossBarComponent) config.create(airdrop, "bar");
        try (var unbound = new EventContext(false)) {
            airdrop.components().add(component, unbound);
            var bar = viewer.getBossBars().iterator().next();
            assertEquals(.5F, bar.progress());
            assertEquals(BossBar.Color.RED, bar.color());
            airdrop.args().putAll(Map.of("radius", "3", "progress", "0.25", "color", "blue", "overlay", "notched_10"));
            airdrop.tick(10*10);
            component.onSyncTick(unbound);
           // assertTrue(viewer.getBossBars().isEmpty());
            assertEquals(.25F, bar.progress());
            assertEquals(BossBar.Color.BLUE, bar.color());
            assertEquals(BossBar.Overlay.NOTCHED_10, bar.overlay());
            component.markRemoved(unbound);
        }
    }

}
