package dev.by1337.baf.component;

import com.google.gson.JsonPrimitive;
import dev.by1337.yaml.YamlValue;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ComponentLifecycleContractTest {
    private static final Set<String> EXCEPTIONS = Set.of(
            "default:__init__", "default:__destroy__", "default:commands");
    private static final List<String> EVENTS = List.of("on_create", "on_remove");

    @TestFactory
    Stream<DynamicTest> everyRegisteredComponentExposesLifecycleEvents() {
        AirdropComponents.boot();
        var schemas = AirdropComponent.REGISTRY.schemas();
        assertFalse(schemas.isEmpty(), "Component registry must be populated");
        assertEquals(AirdropComponent.REGISTRY.size(), schemas.size(), "Every decoder must have a web schema");
        return schemas.entrySet().stream()
                .filter(entry -> !EXCEPTIONS.contains(entry.getKey()))
                .flatMap(entry -> EVENTS.stream().flatMap(event -> Stream.of(
                        DynamicTest.dynamicTest(entry.getKey() + " decodes " + event, () -> {
                            var decoder = AirdropComponent.REGISTRY.get(entry.getKey());
                            assertNotNull(decoder);
                            // Malformed weights must produce an error under this field. An unknown
                            // field would be silently ignored, even if other config fields fail.
                            var result = decoder.decode(YamlValue.wrap(Map.of(
                                    "animation", "test", "count", 1, "timer", 1,
                                    event, Map.of("weights", "not a map"))));
                            assertTrue(result.hasError(), event + " must be decoded as an event handler");
                            assertTrue(result.error().contains(event + ":\n"),
                                    () -> entry.getKey() + " ignores " + event + ": " + result.error());
                        }),
                        DynamicTest.dynamicTest(entry.getKey() + " web exposes " + event, () -> {
                            var schema = entry.getValue();
                            assertTrue(schema.getAsJsonArray("x-events").contains(new JsonPrimitive(event)),
                                    event + " must be an output port");
                            var field = schema.getAsJsonObject("properties").getAsJsonObject(event);
                            assertNotNull(field, event + " must have a configuration field");
                            assertEquals("event", field.get("x-control").getAsString(),
                                    event + " must use the event editor");
                        })
                )));
    }
}
