package dev.by1337.baf.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.by1337.baf.workflow.WorkflowFiles;
import dev.by1337.web.client.RequestRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TunnelEditorTest {
    @TempDir
    Path dir;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private HttpResponse<String> send(EditorDevServer server, String route, String method, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(server.origin() + "/api/" + server.token + "/" + route)).timeout(Duration.ofSeconds(10));
        if (method.equals("POST")) builder.header("Content-Type", "application/json; charset=UTF-8");
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonObject json(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private JsonObject data(EditorDevServer server) throws Exception {
        var response = json(send(server, "airdrop?key=default.yml", "GET", null));
        assertTrue(response.get("ok").getAsBoolean(), response.toString());
        return response.getAsJsonObject("data");
    }

    private JsonObject payload(JsonObject doc) {
        var body = new JsonObject();
        body.add("key", doc.get("key"));
        body.add("revision", doc.get("revision"));
        body.add("nodes", doc.getAsJsonObject("config").get("nodes").deepCopy());
        body.add("metadata", doc.get("metadata").deepCopy());
        return body;
    }

    @Test
    void actualTunnelServesEditorSchemasAndSavesUnicodeLargePostWithBackupAndRevisionCheck() throws Exception {
        Path assets = dir.resolve("static/bairdropflow");
        Files.createDirectories(assets);
        Files.writeString(assets.resolve("index.html"), "<h1>editor</h1>");
        try (var server = new EditorDevServer(dir.resolve("data"), dir.resolve("static"), 0)) {
            var page = http.send(HttpRequest.newBuilder(URI.create(server.link())).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.body().contains("editor"));
            var catalogue = json(send(server, "airdrops", "GET", null)).getAsJsonArray("data");
            assertEquals(1, catalogue.size());
            assertTrue(catalogue.get(0).getAsJsonObject().get("valid").getAsBoolean());
            var schemas = json(send(server, "schemas", "GET", null)).getAsJsonObject("data");
            assertEquals(8, schemas.size());
            assertTrue(schemas.getAsJsonObject("tick:timer").getAsJsonObject("properties").has("timer"));
            assertFalse(schemas.has("default:commands"));
            var schematicFields = schemas.getAsJsonObject("schematic:paster").getAsJsonObject("properties");
            assertEquals("materials", schematicFields.getAsJsonObject("no_place").get("x-control").getAsString());
            assertEquals("material-map", schematicFields.getAsJsonObject("replace").get("x-control").getAsString());
            var generators = json(send(server, "generator-schemas", "GET", null)).getAsJsonObject("data");
            assertTrue(generators.getAsJsonObject("generator:fixed").getAsJsonObject("properties").has("at"));
            assertTrue(generators.getAsJsonObject("generator:random").getAsJsonObject("properties").has("air_tester"));
            var spawners = json(send(server, "spawner-schemas", "GET", null)).getAsJsonObject("data");
            assertTrue(spawners.getAsJsonObject("spawner:timer").getAsJsonObject("properties").has("when"));
            JsonObject yamlBody = new JsonObject();
            yamlBody.addProperty("yaml", "- bc: |-\n    Привет\n    всем\n- all_players:\n    sound: \"bell 1 1\"\n- \"[setblock] 10 10 10 air\"");
            JsonObject parsedYaml = json(send(server, "commands/parse", "POST", yamlBody.toString()));
            assertTrue(parsedYaml.get("ok").getAsBoolean(), parsedYaml.toString());
            assertEquals("Привет\nвсем", parsedYaml.getAsJsonArray("data").get(0).getAsJsonObject().get("bc").getAsString());
            yamlBody.addProperty("yaml", "- create: timer");
            assertFalse(json(send(server, "commands/parse", "POST", yamlBody.toString())).get("ok").getAsBoolean());
            yamlBody.addProperty("yaml", "- '[destroy]'");
            assertFalse(json(send(server, "commands/parse", "POST", yamlBody.toString())).get("ok").getAsBoolean());
            JsonObject original = data(server), body = payload(original);
            var settings = new JsonObject();
            var generator = new JsonObject();
            generator.addProperty("type", "generator:fixed");
            generator.addProperty("at", "10;90;20");
            settings.add("generator", generator);
            var args = new JsonObject();
            args.addProperty("loot_table", "example/default");
            settings.add("args", args);
            var spawner = JsonParser.parseString("""
                    {"type":"spawner:timer","time":"5m",
                     "on_create":[{"weights":{"20":[{"set_value":"loot_table example/epic"}],"80":[{"set_value":"loot_table example/default"}]}}],
                     "when":{"2m":[{"bc":"Через две минуты"}]},
                     "on_timer_end":[{"bc":"Аирдроп появился"},{"make":{"loot_table":"{value:loot_table}"}}]}
                    """).getAsJsonObject();
            settings.add("spawner", spawner);
            body.add("settings", settings);
            // Well above the old 64 KiB tunnel limit, with Cyrillic and control characters.
            String text = "Привет ❤️\t\n".repeat(9000);
            body.getAsJsonObject("nodes").getAsJsonObject("opening").addProperty("editor_note", text);
            body.getAsJsonObject("nodes").getAsJsonObject("opening").addProperty("timer", 45);
            JsonArray actions = new JsonArray();
            JsonObject broadcast = new JsonObject();
            broadcast.addProperty("bc", "<gradient:#ff0000:#00ff00>Привет\nвсем</gradient>");
            actions.add(broadcast);
            actions.add("[setblock] 10 10 10 air");
            body.getAsJsonObject("nodes").getAsJsonObject("opening").add("on_create", actions);
            JsonObject point = new JsonObject();
            point.addProperty("x", 123);
            point.addProperty("y", -42);
            JsonObject positions = new JsonObject();
            positions.add("opening", point);
            body.getAsJsonObject("metadata").add("nodes", positions);
            var saved = json(send(server, "airdrop", "POST", body.toString()));
            assertTrue(saved.get("ok").getAsBoolean(), saved.toString());
            JsonObject roundTrip = data(server);
            assertEquals(45, roundTrip.getAsJsonObject("config").getAsJsonObject("nodes").getAsJsonObject("opening").get("timer").getAsInt());
            assertEquals(generator, roundTrip.getAsJsonObject("config").getAsJsonObject("generator"));
            assertEquals(args, roundTrip.getAsJsonObject("config").getAsJsonObject("args"));
            assertEquals(spawner, roundTrip.getAsJsonObject("config").getAsJsonObject("spawner"));
            assertEquals(actions, roundTrip.getAsJsonObject("config").getAsJsonObject("nodes").getAsJsonObject("opening").get("on_create"));
            assertEquals(text, roundTrip.getAsJsonObject("config").getAsJsonObject("nodes").getAsJsonObject("opening").get("editor_note").getAsString());
            assertTrue(roundTrip.getAsJsonObject("config").getAsJsonObject("custom_field").get("preserved").getAsBoolean());
            assertTrue(roundTrip.getAsJsonObject("config").get("$web_meta").isJsonPrimitive());
            assertEquals(point, roundTrip.getAsJsonObject("metadata").getAsJsonObject("nodes").get("opening"));
            assertNotEquals(original.get("revision"), roundTrip.get("revision"));
            try (var backups = Files.walk(dir.resolve("data/backups"))) {
                var files = backups.filter(Files::isRegularFile).toList();
                assertEquals(1, files.size());
                assertEquals(EditorDevServer.EXAMPLE, Files.readString(files.getFirst()));
            }
            var conflict = json(send(server, "airdrop", "POST", body.toString()));
            assertFalse(conflict.get("ok").getAsBoolean());
            assertEquals("conflict", conflict.get("code").getAsString());
            JsonObject invalid = payload(roundTrip);
            invalid.getAsJsonObject("nodes").getAsJsonObject("opening").addProperty("type", "unknown:type");
            assertFalse(json(send(server, "airdrop", "POST", invalid.toString())).get("ok").getAsBoolean());
            assertEquals(roundTrip.get("revision"), data(server).get("revision"));
            assertEquals(404, send(server, "airdrops", "POST", "{}").statusCode());
            assertEquals(405, send(server, "airdrop", "DELETE", null).statusCode());
            assertEquals(413, send(server, "airdrop", "POST", "x".repeat(512 * 1024 + 1)).statusCode());
        }
    }

    @Test
    void rejectsPathTraversalAndInvalidEditsBeforeBackup() throws Exception {
        Path home = dir.resolve("workflow");
        Files.createDirectories(home);
        Files.writeString(home.resolve("demo.yml"), "id: keep\nunknown: yes\nnodes: {}\n");
        Files.writeString(dir.resolve("outside.yml"), "private: true");
        var files = new WorkflowFiles(home, dir.resolve("backups"));
        assertThrows(IllegalArgumentException.class, () -> files.read("../outside.yml"));
        Files.createSymbolicLink(home.resolve("escape.yml"), dir.resolve("outside.yml"));
        assertEquals(java.util.List.of("demo.yml"), files.keys());
        var original = files.read("demo.yml");
        assertThrows(IllegalArgumentException.class, () -> files.save("demo.yml", original.revision(), new JsonObject(), new JsonObject(), null,
                yaml -> {throw new IllegalArgumentException("invalid");}));
        assertFalse(Files.exists(dir.resolve("backups")));
        assertThrows(IllegalArgumentException.class, () -> JsonValues.raw(new JsonPrimitive("bad\u0001")));
        assertEquals(original.revision(), files.read("demo.yml").revision());
        assertThrows(IllegalArgumentException.class, () -> files.save("demo.yml", original.revision(), new JsonObject(), new JsonObject(),
                JsonParser.parseString("{\"unknown\":\"changed\"}").getAsJsonObject(), yaml -> {}));
    }

    @Test
    void requestRouterSeparatesMethodsAndSerializesNullsAndControlCharacters() {
        RequestRouter router = new RequestRouter().route("/same", h -> {
            try (var ignored = h.writer().startObject(null)) {
                h.writer().putString("method", h.params().method());
            }
            h.send();
        }).post("/same", h -> {
            try (var ignored = h.writer().startObject(null)) {
                h.writer().putString("method", h.params().method());
                h.writer().putString("body", h.params().body());
                h.writer().putString("name", h.params().getString("name"));
                h.writer().putNull("empty");
            }
            h.send();
        });
        AtomicReference<String> result = new AtomicReference<>();
        String body = "привет\u0001";
        router.handle("POST", "/same?name=%D0%9D%D0%BE%D0%B4%D0%B0", "application/json", body.getBytes(StandardCharsets.UTF_8), (payload, off, len, isJson) -> result.set(new String(payload, off, len, StandardCharsets.UTF_8)));
        var parsed = JsonParser.parseString(result.get()).getAsJsonObject();
        assertEquals("POST", parsed.get("method").getAsString());
        assertEquals("Нода", parsed.get("name").getAsString());
        assertEquals(body, parsed.get("body").getAsString());
        assertTrue(parsed.get("empty").isJsonNull());
        router.handle("/same", (payload, off, len, isJson) -> result.set(new String(payload, off, len, StandardCharsets.UTF_8)));
        assertEquals("GET", JsonParser.parseString(result.get()).getAsJsonObject().get("method").getAsString());
    }
}
