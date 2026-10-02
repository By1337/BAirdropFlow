package dev.by1337.baf.web;

import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventDslYamlTest {
    @Test
    void parsesRecursiveActionsAndPreservesTextKeys() {
        JsonArray actions = CommandYaml.parse("""
                - if: '%vault_eco_balance% > 1000'
                  do:
                    - if-all:
                        - '%player_name% == _By1337_'
                        - '100 > 1'
                      do: 'message'
                  else:
                    - if-one:
                        - '100 < -1'
                        - '100 > 1'
                      do:
                        - bc: 'Недостаточно денег'
                - oneOf:
                    - if: 'first'
                      do: 'first message'
                    - do: 'Fallback'
                - weights:
                    '10,7':
                      - set_value: loot_table example/epic
                      - some_plugin_command:
                          nested: value
                    '20,9':
                      - console: 'say ok'
                """);
        assertEquals(3, actions.size());
        assertEquals("message", actions.get(0).getAsJsonObject().getAsJsonArray("do").get(0).getAsJsonObject().get("do").getAsString());
        assertEquals("Fallback", actions.get(1).getAsJsonObject().getAsJsonArray("oneOf").get(1).getAsJsonObject().get("do").getAsString());
        var weights = actions.get(2).getAsJsonObject().getAsJsonObject("weights");
        assertEquals("loot_table example/epic", weights.getAsJsonArray("10,7").get(0).getAsJsonObject().get("set_value").getAsString());
        assertEquals("say ok", weights.getAsJsonArray("20,9").get(0).getAsJsonObject().get("console").getAsString());
        assertEquals("value", weights.getAsJsonArray("10,7").get(1).getAsJsonObject().getAsJsonObject("some_plugin_command").get("nested").getAsString());
    }
}
