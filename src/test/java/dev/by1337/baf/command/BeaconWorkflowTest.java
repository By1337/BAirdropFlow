package dev.by1337.baf.command;

import dev.by1337.baf.*;
import dev.by1337.baf.component.*;
import dev.by1337.baf.component.impl.CommandsComponent;
import dev.by1337.baf.component.impl.TimerComponent;
import dev.by1337.baf.spawner.AirdropSpawner;
import dev.by1337.cmd.Command;
import dev.by1337.cmd.argument.ArgumentCommand;
import dev.by1337.core.command.bcmd.argument.ArgumentInt;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.*;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BeaconWorkflowTest {
    private static EventHandler handler(YamlMap node, String key) {
        return node.has(key) ? ComponentFactory.HANDLER.decode(node.get(key)).getOrThrow() : ComponentFactory.EMPTY_HANDLER;
    }
    private record WorldNode(YamlMap yaml) implements ComponentFactory {
        public AirdropComponent create(Airdrop air, String name) {
            return new AbstractComponent(name, air.components()) {
                public void onCreate(EventContext ctx) { handler(yaml, "on_create").handle(ctx); }
                public void onRemove(EventContext ctx) { handler(yaml, "on_remove").handle(ctx); }
            };
        }
        public YamlDecoder<? extends ComponentFactory> codec() { return null; }
    }

    @Test void chestsCloseAndTntStartsFiveSecondsBeforeEndWithMatchingHolograms() throws Exception {
        File source = new File("CONFIGS/workflow/beacon.yml");
        if (!source.isFile()) return;
        try (var initialize = new EventContext(false)) {}
        MockBukkit.mock();
        try {
            var yaml = YamlMap.load(source).get("nodes").asYamlMap().getOrThrow();
            Map<String, ComponentFactory> factories = new HashMap<>();
            for (var entry : yaml.getRaw().entrySet()) {
                var node = YamlValue.wrap(entry.getValue()).asYamlMap().getOrThrow();
                ComponentFactory factory = switch (node.get("type").asString("")) {
                    case "default:commands" -> CommandsComponent.Config.DECODER.decode(node.get()).getOrThrow();
                    case "tick:timer" -> TimerComponent.Config.DECODER.decode(node.get()).getOrThrow();
                    default -> new WorldNode(node); // The real world, GUI and NMS renderers are tested separately.
                };
                factories.put(entry.getKey(), factory);
            }
            var config = new Workflow("test", factories, null, Map.of(), "world", mock(AirdropSpawner.class), 30);
            var manager = mock(AirdropManager.class);
            var air = new Airdrop(config, manager, new Location(MockBukkit.getMock().addSimpleWorld("world"), 0, 64, 0));
            var method = AirdropCommands.class.getDeclaredMethod("nodeControl"); method.setAccessible(true);
            @SuppressWarnings("unchecked") Command<EventContext> commands = (Command<EventContext>) method.invoke(null);
            // No nearby players in this phase test; parse the original nested commands without executing payouts.
            commands.sub(new Command<EventContext>("[nearby]").executor(new ArgumentInt<>("radius"),
                    new ArgumentCommand<>("command", () -> commands), (ctx, radius, command) -> {}));
            try (var ctx = new EventContext(false, null, Airdrop.PLACEHOLDERS.bindCtx(air)).setCommand(commands)) {
                ctx.with(air); ctx.with(air.location());
                air.components().add(factories.get("closed_1").create(air, "closed_1"), ctx);
                for (int i = 1; i <= 4; i++) {
                    assertNotNull(air.components().get("chest_" + i + "_closed_holo"));
                }
                air.components().add(factories.get("open_chests").create(air, "open_chests"), ctx);
                for (int i = 1; i <= 4; i++) {
                    assertNull(air.components().get("chest_" + i + "_closed_holo"));
                    assertNotNull(air.components().get("loot_" + i));
                    assertNotNull(air.components().get("chest_" + i + "_open_holo"));
                }
                var active = (TimerComponent) factories.get("active").create(air, "active");
                air.components().add(active, ctx);
                for (int tick = 0; tick < 54 * 20; tick++) active.onSyncTick(ctx);
                assertNull(air.components().get("ending_tnt"));
                for (int tick = 0; tick < 20; tick++) active.onSyncTick(ctx);
                assertEquals(5, active.getPlaceholder("timer"));
                var tnt = air.components().get("ending_tnt"); assertNotNull(tnt);
                for (int i = 1; i <= 4; i++) {
                    assertNull(air.components().get("loot_" + i));
                    assertNull(air.components().get("chest_" + i + "_open_holo"));
                    assertNotNull(air.components().get("closed_" + i));
                    assertNotNull(air.components().get("chest_" + i + "_closed_holo"));
                }
                for (int tick = 0; tick < 100; tick++) active.onSyncTick(ctx);
                assertTrue(active.isRemoved()); assertFalse(air.isRemoved()); assertSame(tnt, air.components().get("ending_tnt"));
                handler(yaml.get("ending_tnt").asYamlMap().getOrThrow(), "on_finish").handle(ctx);
                assertTrue(air.isRemoved()); verify(manager).removeAirdrop(air, ctx);
            }
        } finally { MockBukkit.unmock(); }
    }
}
