package dev.by1337.baf.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.by1337.baf.workflow.WorkflowFiles;
import dev.by1337.web.client.RequestRouter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

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

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }
    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void copiesTheEntireWorkflowWithoutChangingTheSource() throws Exception {
        Path root = dir.resolve("copy-data");
        try (var server = new EditorDevServer(root, dir.resolve("static"), 0)) {
            var edit = payload(data(server));
            edit.getAsJsonObject("metadata").add("webNodes", JsonParser.parseString("{\"note\":{\"text\":\"Заметка\",\"x\":123,\"color\":\"#abcdef\"}}"));
            edit.getAsJsonObject("metadata").add("viewport", JsonParser.parseString("{\"x\":20,\"y\":30,\"scale\":0.7}"));
            assertTrue(json(send(server, "airdrop", "POST", edit.toString())).get("ok").getAsBoolean());
            var original = data(server);
            Path source = root.resolve("workflow/default.yml");
            byte[] originalBytes = Files.readAllBytes(source);
            var request = new JsonObject();
            request.addProperty("source", "default.yml");
            request.addProperty("key", "копия.yml");
            request.addProperty("id", "demo:копия");
            var response = json(send(server, "airdrop/copy", "POST", request.toString()));
            assertTrue(response.get("ok").getAsBoolean(), response.toString());
            var copied = response.getAsJsonObject("data");
            var expected = original.getAsJsonObject("config").deepCopy();
            expected.addProperty("id", "demo:копия");
            assertEquals(expected, copied.getAsJsonObject("config"));
            assertEquals(original.get("metadata"), copied.get("metadata"));
            assertNotNull(server.workflows.getById("demo:копия"));
            assertArrayEquals(originalBytes, Files.readAllBytes(source));
            Path destination = root.resolve("workflow/копия.yml");
            byte[] copyBytes = Files.readAllBytes(destination);
            request.addProperty("id", "another-id");
            assertFalse(json(send(server, "airdrop/copy", "POST", request.toString())).get("ok").getAsBoolean());
            assertArrayEquals(copyBytes, Files.readAllBytes(destination));
            request.addProperty("key", "duplicate-id.yml");
            request.addProperty("id", "demo:копия");
            assertFalse(json(send(server, "airdrop/copy", "POST", request.toString())).get("ok").getAsBoolean());
            assertFalse(Files.exists(root.resolve("workflow/duplicate-id.yml")));
            request.addProperty("id", "another-id");
            for (String key : new String[]{"../outside.yml", "/tmp/outside.yml", "bad.txt"}) {
                request.addProperty("key", key);
                assertFalse(json(send(server, "airdrop/copy", "POST", request.toString())).get("ok").getAsBoolean());
            }
            request.addProperty("key", "missing-source.yml");
            request.addProperty("source", "../outside.yml");
            assertFalse(json(send(server, "airdrop/copy", "POST", request.toString())).get("ok").getAsBoolean());
            assertFalse(Files.exists(root.resolve("workflow/missing-source.yml")));
            var copyEdit = payload(copied);
            copyEdit.getAsJsonObject("nodes").getAsJsonObject("opening").addProperty("timer", "99");
            assertTrue(json(send(server, "airdrop", "POST", copyEdit.toString())).get("ok").getAsBoolean());
            assertArrayEquals(originalBytes, Files.readAllBytes(source));
        }
    }

    @Test
    void createsAValidAirdropAndRejectsDuplicateFilesIdsAndInvalidPaths() throws Exception {
        Path dataRoot = dir.resolve("create-data");
        try (var server = new EditorDevServer(dataRoot, dir.resolve("static"), 0)) {
            var request = new JsonObject();
            request.addProperty("key", "новый-аирдроп.yml");
            request.addProperty("id", "demo:новый");
            request.addProperty("world", "world_nether");
            var response = json(send(server, "airdrop/create", "POST", request.toString()));
            assertTrue(response.get("ok").getAsBoolean(), response.toString());
            var created = response.getAsJsonObject("data");
            var config = created.getAsJsonObject("config");
            assertEquals("demo:новый", config.get("id").getAsString());
            assertEquals("world_nether", config.get("world").getAsString());
            assertTrue(config.getAsJsonObject("nodes").has("__init__"));
            assertTrue(config.getAsJsonObject("nodes").has("__destroy__"));
            assertNotNull(server.workflows.get("новый-аирдроп.yml"));
            var list = json(send(server, "airdrops", "GET", null)).getAsJsonArray("data");
            assertEquals(2, list.size());
            for (var summary : list) assertTrue(summary.getAsJsonObject().get("valid").getAsBoolean());
            Path file = dataRoot.resolve("workflow/новый-аирдроп.yml");
            byte[] original = Files.readAllBytes(file);

            request.addProperty("id", "different-id");
            var duplicateFile = json(send(server, "airdrop/create", "POST", request.toString()));
            assertFalse(duplicateFile.get("ok").getAsBoolean());
            assertTrue(duplicateFile.get("error").getAsString().contains("уже существует"));
            assertArrayEquals(original, Files.readAllBytes(file));
            request.addProperty("key", "duplicate-id.yml");
            request.addProperty("id", "demo:новый");
            assertFalse(json(send(server, "airdrop/create", "POST", request.toString())).get("ok").getAsBoolean());
            assertFalse(Files.exists(dataRoot.resolve("workflow/duplicate-id.yml")));

            request.addProperty("id", "different-id");
            for (String key : new String[]{"../outside.yml", "/tmp/outside.yml", "folder/new.yml", "bad.txt", ""}) {
                request.addProperty("key", key);
                assertFalse(json(send(server, "airdrop/create", "POST", request.toString())).get("ok").getAsBoolean(), key);
            }
            assertFalse(Files.exists(dataRoot.resolve("outside.yml")));
            request.addProperty("key", "blank.yml");
            request.addProperty("world", " ");
            assertFalse(json(send(server, "airdrop/create", "POST", request.toString())).get("ok").getAsBoolean());
            assertFalse(Files.exists(dataRoot.resolve("workflow/blank.yml")));

            var edit = payload(created);
            var commands = new JsonObject();
            commands.addProperty("type", "default:commands");
            var actions = new JsonArray();
            var console = new JsonObject();
            console.addProperty("console", "say new airdrop");
            actions.add(console);
            commands.add("on_run", actions);
            edit.getAsJsonObject("nodes").add("commands", commands);
            var saved = json(send(server, "airdrop", "POST", edit.toString()));
            assertTrue(saved.get("ok").getAsBoolean(), saved.toString());
            assertTrue(Files.readString(file).contains("say new airdrop"));
        }
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
            var timeout = schemas.getAsJsonObject("tick:timeout");
            assertEquals(5, timeout.getAsJsonObject("properties").size()); // type, ticks and three outputs
            assertEquals(1, timeout.getAsJsonObject("properties").getAsJsonObject("ticks").get("minimum").getAsInt());
            assertEquals(JsonParser.parseString("[\"create\",\"remove\"]"), timeout.getAsJsonArray("x-inputs"));
            assertEquals(JsonParser.parseString("[\"on_create\",\"on_remove\",\"on_timer_end\"]"), timeout.getAsJsonArray("x-events"));
            assertTrue(schemas.getAsJsonObject("tick:timer").getAsJsonObject("properties").has("timer"));
            assertEquals("Осталось секунд", schemas.getAsJsonObject("tick:timer").getAsJsonObject("x-placeholders")
                    .getAsJsonObject("timer").get("title").getAsString());
            assertTrue(schemas.getAsJsonObject("block:break").getAsJsonObject("x-placeholders").has("count"));
            var bossbar = schemas.getAsJsonObject("default:bossbar");
            assertTrue(bossbar.getAsJsonObject("x-placeholders").has("time"));
            var bossbarFields = bossbar.getAsJsonObject("properties");
            assertEquals(JsonParser.parseString("[\"boolean\",\"string\"]"), bossbarFields.getAsJsonObject("progress_from_time").get("type"));
            assertEquals(JsonParser.parseString("[\"number\",\"string\"]"), bossbarFields.getAsJsonObject("radius").get("type"));
            assertEquals(1D, bossbarFields.getAsJsonObject("progress").get("maximum").getAsDouble());
            assertEquals(7, bossbarFields.getAsJsonObject("color").getAsJsonArray("anyOf").get(0).getAsJsonObject().getAsJsonArray("enum").size());
            assertEquals("[{%]", bossbarFields.getAsJsonObject("color").getAsJsonArray("anyOf").get(1).getAsJsonObject().get("pattern").getAsString());
            var particleSchema = schemas.getAsJsonObject("particle:animation");
            assertTrue(particleSchema.getAsJsonObject("properties").has("animation"));
            assertTrue(particleSchema.getAsJsonObject("properties").has("ticks"));
            assertTrue(particleSchema.getAsJsonObject("properties").has("offsets"));
            assertFalse(particleSchema.getAsJsonObject("properties").has("infinity"));
            var commands = schemas.getAsJsonObject("default:commands");
            assertEquals("on_run", commands.getAsJsonArray("x-events").get(0).getAsString());
            assertEquals("run", commands.getAsJsonArray("x-inputs").get(0).getAsString());
            assertFalse(schemas.getAsJsonObject("default:__init__").getAsJsonArray("x-events").contains(new JsonPrimitive("on_create")));
            assertFalse(schemas.getAsJsonObject("default:__destroy__").getAsJsonArray("x-events").contains(new JsonPrimitive("on_remove")));
            for (String type : new String[]{"schematic:paster", "wg:region"}) {
                var events = schemas.getAsJsonObject(type).getAsJsonArray("x-events");
                assertTrue(events.contains(new JsonPrimitive("on_create")), type);
                assertTrue(events.contains(new JsonPrimitive("on_remove")), type);
            }
            var schematicFields = schemas.getAsJsonObject("schematic:paster").getAsJsonObject("properties");
            assertEquals("materials", schematicFields.getAsJsonObject("no_place").get("x-control").getAsString());
            assertEquals("material-map", schematicFields.getAsJsonObject("replace").get("x-control").getAsString());
            var generators = json(send(server, "generator-schemas", "GET", null)).getAsJsonObject("data");
            assertTrue(generators.getAsJsonObject("generator:fixed").getAsJsonObject("properties").has("at"));
            assertTrue(generators.getAsJsonObject("generator:random").getAsJsonObject("properties").has("air_tester"));
            var spawners = json(send(server, "spawner-schemas", "GET", null)).getAsJsonObject("data");
            assertTrue(spawners.getAsJsonObject("spawner:timer").getAsJsonObject("properties").has("when"));
            var gpsSchema = spawners.getAsJsonObject("spawner:timer").getAsJsonObject("properties").getAsJsonObject("gps");
            assertFalse(gpsSchema.getAsJsonObject("default").get("publish").getAsBoolean());
            assertTrue(gpsSchema.getAsJsonObject("properties").has("publish_location"));
            assertTrue(gpsSchema.getAsJsonObject("properties").has("gps_line"));
            JsonObject yamlBody = new JsonObject();
            yamlBody.addProperty("yaml", "- bc: |-\n    Привет\n    всем\n- all_players:\n    sound: \"bell 1 1\"\n- \"[setblock] 10 10 10 air\"");
            JsonObject parsedYaml = json(send(server, "commands/parse", "POST", yamlBody.toString()));
            assertTrue(parsedYaml.get("ok").getAsBoolean(), parsedYaml.toString());
            assertEquals("Привет\nвсем", parsedYaml.getAsJsonArray("data").get(0).getAsJsonObject().get("bc").getAsString());
            yamlBody.addProperty("yaml", "- setblock: '{x} {y} {z} minecraft:respawn_anchor[charges=4]'\n- console: '123213'");
            var commandList = json(send(server, "commands/parse", "POST", yamlBody.toString())).getAsJsonArray("data");
            assertEquals(2, commandList.size());
            assertEquals("{x} {y} {z} minecraft:respawn_anchor[charges=4]", commandList.get(0).getAsJsonObject().get("setblock").getAsString());
            yamlBody.addProperty("yaml", "- create: timer");
            assertTrue(json(send(server, "commands/parse", "POST", yamlBody.toString())).get("ok").getAsBoolean());
            yamlBody.addProperty("yaml", "- '[destroy]'");
            assertTrue(json(send(server, "commands/parse", "POST", yamlBody.toString())).get("ok").getAsBoolean());
            yamlBody.addProperty("yaml", "- if: ready\n  do:\n    - create: timer\n  else:\n    - call: update hologram");
            var conditional = json(send(server, "commands/parse", "POST", yamlBody.toString())).getAsJsonArray("data");
            assertEquals("timer", conditional.get(0).getAsJsonObject().getAsJsonArray("do").get(0).getAsJsonObject().get("create").getAsString());
            JsonObject original = data(server), body = payload(original);
            var settings = new JsonObject();
            var generator = new JsonObject();
            generator.addProperty("type", "generator:fixed");
            generator.addProperty("at", "{position}");
            settings.add("generator", generator);
            var defaults = JsonParser.parseString("{\"delay\":\"45\",\"position\":\"10;90;20\"}").getAsJsonObject();
            settings.add("defaults", defaults);
            var args = new JsonObject();
            args.addProperty("loot_table", "example/default");
            args.addProperty("rolls", "90");
            args.addProperty("offset", "0;1;0");
            args.addProperty("mask", "gray_dye");
            settings.add("args", args);
            var spawner = JsonParser.parseString("""
                    {"type":"spawner:timer","time":"5m",
                     "gps":{"publish":true,"publish_location":true,"gps_line":"{color}Аирдроп через {formated_time_left}: {world} {x} {y} {z}"},
                     "on_create":[{"weights":{"20":[{"set_value":"loot_table example/epic"}],"80":[{"set_value":"loot_table example/default"}]}}],
                     "when":{"2m":[{"bc":"Через две минуты"}]},
                     "on_timer_end":[{"bc":"Аирдроп появился"},{"make":{"loot_table":"{value:loot_table}"}}]}
                    """).getAsJsonObject();
            settings.add("spawner", spawner);
            body.add("settings", settings);
            // Well above the old 64 KiB tunnel limit, with Cyrillic and control characters.
            String text = "Привет ❤️\t\n".repeat(9000);
            var dynamicLoot = JsonParser.parseString("""
                    {"type":"loot:looter","loot_table":"{args:loot_table}","rolls":"{args:rolls}",
                     "offsets":"{args:offset}","obfuscation_materials":["{args:mask}"],"fill_duration_ticks":"200"}
                    """).getAsJsonObject();
            body.getAsJsonObject("nodes").add("dynamic_loot", dynamicLoot);
            body.getAsJsonObject("nodes").getAsJsonObject("opening").addProperty("editor_note", text);
            body.getAsJsonObject("nodes").getAsJsonObject("opening").addProperty("timer", "{delay}");
            JsonArray actions = new JsonArray();
            JsonObject broadcast = new JsonObject();
            broadcast.addProperty("bc", "<gradient:#ff0000:#00ff00>Привет\nвсем</gradient>");
            actions.add(broadcast);
            actions.add("[setblock] 10 10 10 air");
            body.getAsJsonObject("nodes").getAsJsonObject("opening").add("on_create", actions);
            body.getAsJsonObject("nodes").add("commands", JsonParser.parseString("""
                    {"type":"default:commands","on_run":[
                      {"setblock":"{x} {y} {z} minecraft:respawn_anchor[charges=4]"},
                      {"console":"123213"}]}
                    """).getAsJsonObject());
            JsonObject point = new JsonObject();
            point.addProperty("x", 123);
            point.addProperty("y", -42);
            JsonObject positions = new JsonObject();
            positions.add("opening", point);
            body.getAsJsonObject("metadata").add("nodes", positions);
            body.getAsJsonObject("metadata").add("defaultsNode", point);
            assertEquals(original.get("revision").getAsString(), data(server).get("revision").getAsString());
            var saved = json(send(server, "airdrop", "POST", body.toString()));
            assertTrue(saved.get("ok").getAsBoolean(), saved.toString());
            assertTrue(Files.readString(dir.resolve("data/workflow/default.yml")).contains("minecraft:respawn_anchor[charges=4]"));
            JsonObject roundTrip = data(server);
            assertEquals(dynamicLoot, roundTrip.getAsJsonObject("config").getAsJsonObject("nodes").getAsJsonObject("dynamic_loot"));
            assertEquals(2, roundTrip.getAsJsonObject("config").getAsJsonObject("nodes").getAsJsonObject("commands").getAsJsonArray("on_run").size());
            assertEquals("{delay}", roundTrip.getAsJsonObject("config").getAsJsonObject("nodes").getAsJsonObject("opening").get("timer").getAsString());
            assertEquals(defaults, roundTrip.getAsJsonObject("config").getAsJsonObject("defaults"));
            assertEquals(generator, roundTrip.getAsJsonObject("config").getAsJsonObject("generator"));
            assertEquals(args, roundTrip.getAsJsonObject("config").getAsJsonObject("args"));
            assertEquals(spawner, roundTrip.getAsJsonObject("config").getAsJsonObject("spawner"));
            assertEquals(actions, roundTrip.getAsJsonObject("config").getAsJsonObject("nodes").getAsJsonObject("opening").get("on_create"));
            assertEquals(text, roundTrip.getAsJsonObject("config").getAsJsonObject("nodes").getAsJsonObject("opening").get("editor_note").getAsString());
            assertTrue(roundTrip.getAsJsonObject("config").getAsJsonObject("custom_field").get("preserved").getAsBoolean());
            assertTrue(roundTrip.getAsJsonObject("config").get("$web_meta").isJsonPrimitive());
            assertEquals(point, roundTrip.getAsJsonObject("metadata").getAsJsonObject("nodes").get("opening"));
            assertEquals(point, roundTrip.getAsJsonObject("metadata").get("defaultsNode"));
            assertTrue(Files.readString(dir.resolve("data/workflow/default.yml")).contains("{delay}"));
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
