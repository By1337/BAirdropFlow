package dev.by1337.baf.command;

import dev.by1337.baf.*;
import dev.by1337.baf.component.*;
import dev.by1337.baf.component.impl.CommandsComponent;
import dev.by1337.baf.component.impl.InitComponent;
import dev.by1337.baf.component.impl.TimerComponent;
import dev.by1337.baf.spawner.AirdropSpawner;
import dev.by1337.cmd.Command;
import dev.by1337.cmd.argument.ArgumentCommand;
import dev.by1337.cmd.argument.ArgumentStrings;
import dev.by1337.core.command.bcmd.argument.ArgumentInt;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.*;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultWorkflowPhasesTest {
    private static EventHandler handler(YamlMap node, String event) {
        return node.has(event) ? ComponentFactory.HANDLER.decode(node.get(event)).getOrThrow() : ComponentFactory.EMPTY_HANDLER;
    }

    // Exercise real commands and timers; world effects provide their real configured lifecycle handlers.
    private record WorldNode(YamlMap yaml, Map<String, Integer> calls) implements ComponentFactory {
        public AirdropComponent create(Airdrop air, String name) {
            return new AbstractComponent(name, air.components()) {
                { for (String event : List.of("update", "erupt")) exportEvent(event, ctx -> calls.merge(name + "." + event, 1, Integer::sum)); }
                public void onCreate(EventContext ctx) { handler(yaml, "on_create").handle(ctx); }
                public void onRemove(EventContext ctx) { handler(yaml, "on_remove").handle(ctx); }
            };
        }
        public YamlDecoder<? extends ComponentFactory> codec() { return null; }
    }

    @TestFactory
    List<DynamicTest> chainsReachActivePhaseAndRemoveTheirOwnDisplays() {
        return List.of("deathchest", "mystic", "meteorite", "volcano", "geyser", "strange-beacon").stream()
                .map(event -> DynamicTest.dynamicTest(event, () -> checkPhases(event))).toList();
    }

    private void checkPhases(String event) throws Exception {
        try (var initialize = new EventContext(false)) {}
        MockBukkit.mock();
        try {
            var nodes = YamlMap.load(new File("src/main/resources/workflow/" + event + ".yml"))
                    .get("nodes").asYamlMap().getOrThrow();
            Map<String, Integer> calls = new HashMap<>();
            Map<String, ComponentFactory> factories = new HashMap<>();
            for (var entry : nodes.getRaw().entrySet()) {
                var node = YamlValue.wrap(entry.getValue()).asYamlMap().getOrThrow();
                factories.put(entry.getKey(), switch (node.get("type").asString("")) {
                    case "default:commands" -> CommandsComponent.Config.DECODER.decode(node.get()).getOrThrow();
                    case "tick:timer" -> TimerComponent.Config.DECODER.decode(node.get()).getOrThrow();
                    case "__init__" -> InitComponent.Config.DECODER.decode(node.get()).getOrThrow();
                    default -> new WorldNode(node, calls);
                });
            }
            var config = new Workflow("test", factories, null, Map.of(), "world", mock(AirdropSpawner.class), 30);
            var manager = mock(AirdropManager.class);
            var air = new Airdrop(config, manager, new Location(MockBukkit.getMock().addSimpleWorld("world"), 0, 64, 0));
            air.args().put("rarity_selected", "1");
            var method = AirdropCommands.class.getDeclaredMethod("nodeControl");
            method.setAccessible(true);
            @SuppressWarnings("unchecked") Command<EventContext> commands = (Command<EventContext>) method.invoke(null);
            commands.sub(new Command<EventContext>("[nearby]").executor(new ArgumentInt<>("radius"),
                    new ArgumentCommand<>("command", () -> commands), (ctx, radius, command) -> {}));
            for (String command : List.of("[bc]", "[sound_all]"))
                commands.sub(new Command<EventContext>(command).executor(new ArgumentStrings<>("value"), (ctx, value) -> {}));
            try (var ctx = new EventContext(false, null, Airdrop.PLACEHOLDERS.bindCtx(air)).setCommand(commands)) {
                ctx.with(air); ctx.with(air.location());
                air.components().add(factories.get("__init__").create(air, "__init__"), ctx);
                boolean strange = event.equals("strange-beacon");
                String waiting = strange ? "activation" : "started";
                if (strange) {
                    assertNull(air.components().get(waiting));
                    handler(nodes.get("summon").asYamlMap().getOrThrow(), "on_build_start").handle(ctx);
                    assertNull(air.components().get(waiting));
                    handler(nodes.get("structure").asYamlMap().getOrThrow(), "on_complete").handle(ctx);
                }
                var timer = (TimerComponent) air.components().get(waiting);
                assertNotNull(timer);
                int seconds = (int) timer.getPlaceholder("timer");
                for (int tick = 0; tick < seconds * 20; tick++) timer.onSyncTick(ctx);
                assertTrue(timer.isRemoved());
                assertNull(air.components().get(waiting + "_holo"));
                assertNull(air.components().get(waiting + "_bar"));
                var active = (TimerComponent) air.components().get("active");
                assertNotNull(active);
                if (!event.equals("geyser")) {
                    assertNotNull(air.components().get("active_holo"));
                    assertTrue(calls.getOrDefault(waiting + "_holo.update", 0) > 0);
                }
                String effect = switch (event) {
                    case "mystic", "deathchest" -> "loot_1";
                    case "meteorite" -> "meteorites";
                    case "strange-beacon" -> "rewards";
                    default -> "spread";
                };
                assertNotNull(air.components().get(effect));
                int activeSeconds = (int) active.getPlaceholder("timer");
                for (int tick = 0; tick < activeSeconds * 20; tick++) active.onSyncTick(ctx);
                assertTrue(active.isRemoved());
                assertNull(air.components().get("active_holo"));
                assertNull(air.components().get("active_bar"));
                if (strange) assertNull(air.components().get("rewards"));
                if (event.equals("geyser")) {
                    assertEquals(1, calls.get("spread.erupt"));
                    assertFalse(air.isRemoved());
                    handler(nodes.get("spread").asYamlMap().getOrThrow(), "on_finish").handle(ctx);
                }
                assertTrue(air.isRemoved());
                verify(manager).removeAirdrop(air, ctx);
            }
        } finally { MockBukkit.unmock(); }
    }
}
