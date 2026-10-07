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

    public void load() throws IOException {
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
            errors.put(doc.key(), result.error());
            logger.accept(doc.key() + ": " + result.error());
        } else {
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
        var saved = files.save(key, revision, nodes, metadata, settings, yaml -> validate(nodes, metadata, yaml));
        update(saved);
        return saved.json();
    }

    private void validate(JsonObject nodes, JsonObject metadata, YamlMap yaml) {
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
    }
}
