package dev.by1337.baf.workflow;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.by1337.baf.Workflow;
import dev.by1337.yaml.YamlMap;
import dev.by1337.yaml.decoder.YamlDecoder;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public final class WorkflowRepository {
    private record Publisher(WorkflowRepository repository, String key) {}

    private dev.by1337.baf.launch.LaunchRegistry launches;
    private final WorkflowFiles files;
    private final YamlDecoder<Workflow> decoder;
    private final Map<String, Workflow> workflows = new LinkedHashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();
    private final Consumer<String> logger;

    public WorkflowRepository(WorkflowFiles files, YamlDecoder<Workflow> decoder, Consumer<String> logger) {
        this.files = files;
        this.decoder = decoder;
        this.logger = logger;
    }

    public void bindRegistry(dev.by1337.baf.launch.LaunchRegistry launches) {
        this.launches = launches;
        workflows.forEach((key, workflow) -> launches.publish(new Publisher(this, key), workflow.id(), workflow));
    }

    public void load() throws IOException {
        if (launches != null) workflows.keySet().forEach(key -> launches.unpublish(new Publisher(this, key)));
        workflows.clear();
        errors.clear();
        for (String key : files.keys()) {
            try {
                update(files.read(key));
            } catch (Exception e) {
                errors.put(key, e.getMessage());
                logger.accept(key + ": " + e.getMessage());
            }
        }
    }

    private void update(WorkflowFiles.Document doc) {
        var result = decoder.decode(doc.yaml().get());
        if (result.hasError() || result.result() == null) {
            workflows.remove(doc.key());
            if (launches != null) launches.unpublish(new Publisher(this, doc.key()));
            errors.put(doc.key(), result.error());
            logger.accept(doc.key() + ": " + result.error());
        } else {
            if (launches != null)
                launches.publish(new Publisher(this, doc.key()), result.result().id(), result.result());
            workflows.put(doc.key(), result.result());
            errors.remove(doc.key());
        }
    }

    public Workflow get(String key) {
        return workflows.get(key);
    }

    public Workflow getById(String id) {
        Workflow result = null;
        for (Workflow workflow : workflows.values()) {
            if (!workflow.id().equals(id)) continue;
            if (result != null) throw new IllegalArgumentException("Duplicate workflow id: " + id);
            result = workflow;
        }
        return result;
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(workflows.keySet());
    }

    public JsonArray list() throws IOException {
        JsonArray result = new JsonArray();
        List<String> keys = files.keys();
        if (launches != null) workflows.keySet().stream().filter(key -> !keys.contains(key)).toList()
                .forEach(key -> launches.unpublish(new Publisher(this, key)));
        workflows.keySet().retainAll(keys);
        errors.keySet().retainAll(keys);
        for (String key : keys) {
            JsonObject summary = new JsonObject();
            summary.addProperty("key", key);
            try {
                var doc = files.read(key);
                update(doc);
                summary.addProperty("id", doc.yaml().get("id").asString(key));
                summary.addProperty("world", doc.yaml().get("world").asString("world"));
                JsonElement nodes = dev.by1337.baf.web.JsonValues.GSON.toJsonTree(doc.yaml().getRaw("nodes"));
                summary.addProperty("nodeCount", nodes.isJsonObject() ? nodes.getAsJsonObject().size() : 0);
                summary.addProperty("valid", !errors.containsKey(key));
                if (errors.containsKey(key)) summary.addProperty("error", errors.get(key));
            } catch (Exception e) {
                summary.addProperty("valid", false);
                summary.addProperty("error", e.getMessage());
            }
            result.add(summary);
        }
        return result;
    }

    public JsonObject read(String key) throws IOException {
        var doc = files.read(key);
        update(doc);
        JsonObject result = doc.json();
        if (errors.containsKey(key)) result.addProperty("validationError", errors.get(key));
        return result;
    }

    public JsonObject save(String key, String revision, JsonObject nodes, JsonObject metadata, JsonObject settings) throws IOException {
        var saved = files.save(key, revision, nodes, metadata, settings, yaml -> validate(nodes, metadata, yaml, key));
        update(saved);
        return saved.json();
    }

    public synchronized JsonObject create(String key, String id, String world) throws IOException {
        if (id == null || id.isBlank() || id.length() > 128)
            throw new IllegalArgumentException("Укажите ID аирдропа (до 128 символов)");
        if (world == null || world.isBlank() || world.length() > 128)
            throw new IllegalArgumentException("Укажите мир (до 128 символов)");
        // Refresh the repository before checking IDs, including files added outside the editor.
        list();
        if (getById(id) != null) throw new IllegalArgumentException("Аирдроп с таким ID уже существует: " + id);
        YamlMap yaml = YamlMap.loadFromString("""
                generator:
                  type: generator:fixed
                  at: '0;100;0'
                spawner:
                  type: spawner:timer
                  time: 5m
                  when: {}
                  on_timer_end:
                    - make: {}
                args: {}
                nodes:
                  __init__:
                    type: __init__
                    on_init: []
                  __destroy__:
                    type: __destroy__
                    on_destroy: []
                """);
        yaml.set("id", id);
        yaml.set("world", world);
        var nodes = dev.by1337.baf.web.JsonValues.GSON.toJsonTree(yaml.getRaw("nodes")).getAsJsonObject();
        var metadata = new JsonObject();
        var created = files.create(key, yaml, candidate -> validate(nodes, metadata, candidate, key));
        update(created);
        return created.json();
    }

    public synchronized JsonObject copy(String source, String key, String id) throws IOException {
        if (id == null || id.isBlank() || id.length() > 128)
            throw new IllegalArgumentException("Укажите ID аирдропа (до 128 символов)");
        list();
        if (getById(id) != null) throw new IllegalArgumentException("Аирдроп с таким ID уже существует: " + id);
        var copied = files.copy(source, key, id, yaml -> {
            var document = new WorkflowFiles.Document(key, "", yaml).json();
            validate(document.getAsJsonObject("config").getAsJsonObject("nodes"), document.getAsJsonObject("metadata"), yaml, key);
        });
        update(copied);
        return copied.json();
    }

    private void validate(JsonObject nodes, JsonObject metadata, YamlMap yaml, String key) {
        if (nodes.size() > 512) throw new IllegalArgumentException("Максимум 512 нод в одном аирдропе");
        for (var node : nodes.entrySet()) {
            String name = node.getKey();
            if (name.isBlank() || name.length() > 128 || name.matches(".*[\\s.{}%\"'\\\\].*") ||
                    Set.of("__proto__", "prototype", "constructor").contains(name) || !node.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Некорректная нода " + node.getKey());
            }
        }
        if (!nodes.has("__init__") || !nodes.has("__destroy__")) {
            throw new IllegalArgumentException("Нужны ноды __init__ и __destroy__");
        }
        if (metadata == null || dev.by1337.baf.web.JsonValues.GSON.toJson(metadata).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536) {
            throw new IllegalArgumentException("Метаданные редактора слишком большие");
        }
        dev.by1337.baf.web.JsonValues.raw(metadata);
        var decoded = decoder.decode(yaml.get());
        if (decoded.hasError() || decoded.result() == null) throw new IllegalArgumentException(decoded.error());
        if (launches != null) launches.checkPublish(new Publisher(this, key), decoded.result().id());
    }
}
