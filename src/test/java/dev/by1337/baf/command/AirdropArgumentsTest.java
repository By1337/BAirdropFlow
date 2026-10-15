package dev.by1337.baf.command;

import dev.by1337.baf.Airdrop;
import dev.by1337.edsl.EventHandler;
import dev.by1337.edsl.context.EventContext;
import dev.by1337.yaml.YamlValue;
import org.bukkit.Location;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirdropArgumentsTest {
    @BeforeAll
    static void initContext() {
        try (var ignored = new EventContext(false)) {
        }
    }

    @Test
    void weightedSelectionKeepsLabelAndLootCountTogetherForEachAirdrop() {
        var server = MockBukkit.mock();
        try {
            var world = server.addSimpleWorld("world");
            var commands = AirdropCommands.argumentControl();
            var handler = EventHandler.decoder().decode(YamlValue.wrap(Map.of("weights", Map.of(
                    "30", List.of(Map.of("set_arg", "air_type <gold>Обычный сундук"), Map.of("set_arg", "loot_count 20")),
                    "1", List.of(Map.of("set_arg", "air_type <red>Всемогущий сундук"), Map.of("set_arg", "loot_count 128"))
            )))).getOrThrow();
            for (int i = 0; i < 100; i++) {
                var air = new Airdrop(null, null, new Location(world, 0, 64, 0));
                air.args().put("player", "Summoner");
                try (var ctx = new EventContext(false, null, Airdrop.PLACEHOLDERS.bindCtx(air));
                     var scope = ctx.with(air)) {
                    ctx.setCommand(commands);
                    handler.handle(ctx);
                    String level = ctx.setPlaceholders("{args:air_type}");
                    assertTrue(List.of("<gold>Обычный сундук", "<red>Всемогущий сундук").contains(level));
                    assertEquals(level.equals("<gold>Обычный сундук") ? "20" : "128", ctx.setPlaceholders("{args:loot_count}"));
                    assertEquals("Summoner", air.args().get("player"));
                }
            }
        } finally {
            MockBukkit.unmock();
        }
    }
}
