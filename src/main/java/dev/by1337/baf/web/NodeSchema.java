package dev.by1337.baf.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Arrays;

/** A component's public editor contract; decoding remains authoritative. */
public final class NodeSchema {
    private final JsonObject schema = new JsonObject();
    private final JsonObject properties = new JsonObject();
    private final JsonArray required = new JsonArray();
    private final JsonArray events = new JsonArray();
    private final JsonArray inputs = new JsonArray();
    private final JsonObject placeholders = new JsonObject();

    private NodeSchema(String title, String description) {
        schema.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.addProperty("title", title);
        schema.addProperty("description", description);
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        schema.add("x-events", events);
        schema.add("x-inputs", inputs);
        schema.add("x-placeholders", placeholders);
        schema.addProperty("additionalProperties", true);
        required.add("type");
    }

    public static NodeSchema of(String title, String description) { return new NodeSchema(title, description); }

    private JsonObject field(String key, String title, String type) {
        JsonObject field = new JsonObject();
        field.addProperty("title", title);
        field.addProperty("type", type);
        properties.add(key, field);
        return field;
    }

    public NodeSchema text(String key, String title, String defaultValue, boolean mandatory) {
        field(key, title, "string").addProperty("default", defaultValue);
        if (mandatory) required.add(key);
        return this;
    }

    public NodeSchema integer(String key, String title, int defaultValue, int minimum, boolean mandatory) {
        JsonObject f = field(key, title, "integer");
        f.addProperty("default", defaultValue);
        f.addProperty("minimum", minimum);
        if (mandatory) required.add(key);
        return this;
    }

    public NodeSchema number(String key, String title, double defaultValue, double minimum, Double maximum) {
        JsonObject f = field(key, title, "number");
        f.addProperty("default", defaultValue);
        f.addProperty("minimum", minimum);
        if (maximum != null) f.addProperty("maximum", maximum);
        return this;
    }

    public NodeSchema bool(String key, String title, boolean defaultValue) {
        field(key, title, "boolean").addProperty("default", defaultValue);
        return this;
    }

    public NodeSchema choice(String key, String title, String defaultValue, String... choices) {
        JsonObject f = field(key, title, "string");
        f.addProperty("default", defaultValue);
        JsonArray values = new JsonArray();
        Arrays.stream(choices).forEach(values::add);
        f.add("enum", values);
        return this;
    }


    public NodeSchema vector(String key, String title, String defaultValue, boolean fractional) {
        JsonObject f = field(key, title, "string");
        f.addProperty("default", defaultValue);
        f.addProperty("x-control", "vector");
        f.addProperty("x-fractional", fractional);
        f.addProperty("description", fractional ? "Дробные координаты X, Y, Z" : "Целые координаты X, Y, Z");
        return this;
    }

    public NodeSchema strings(String key, String title) {
        JsonObject f = field(key, title, "array");
        JsonObject item = new JsonObject(); item.addProperty("type", "string");
        f.add("items", item); f.add("default", new JsonArray()); f.addProperty("x-control", "lines");
        return this;
    }

    public NodeSchema materials(String key, String title) {
        JsonObject f = field(key, title, "array");
        JsonObject item = new JsonObject(); item.addProperty("type", "string");
        f.add("items", item); f.add("default", new JsonArray()); f.addProperty("x-control", "materials");
        return this;
    }

    public NodeSchema materialMap(String key, String title) {
        JsonObject f = field(key, title, "object");
        JsonObject value = new JsonObject(); value.addProperty("type", "string");
        f.add("additionalProperties", value); f.add("default", new JsonObject()); f.addProperty("x-control", "material-map");
        return this;
    }

    public NodeSchema duration(String key, String title, String defaultValue) {
        JsonObject f = field(key, title, "string");
        f.addProperty("default", defaultValue);
        f.addProperty("x-control", "duration");
        required.add(key);
        return this;
    }

    public NodeSchema timeEvents(String key, String title) {
        JsonObject f = field(key, title, "object");
        f.addProperty("x-control", "time-events");
        f.add("default", new JsonObject());
        required.add(key);
        return this;
    }

    public NodeSchema events(String... keys) {
        for (String key : keys) {
            JsonObject f = new JsonObject();
            f.addProperty("title", key); f.addProperty("x-control", "event"); f.add("default", new JsonArray());
            JsonArray alternatives = new JsonArray();
            for (String type : new String[]{"string", "array", "object"}) {
                JsonObject alt = new JsonObject(); alt.addProperty("type", type); alternatives.add(alt);
            }
            f.add("anyOf", alternatives);
            f.addProperty("description", "Команды события; связи между нодами настраиваются на схеме.");
            properties.add(key, f); events.add(key);
        }
        return this;
    }

    public NodeSchema inputs(String... values) { Arrays.stream(values).forEach(inputs::add); return this; }

    public NodeSchema placeholder(String key, String title, String description) {
        JsonObject info = new JsonObject();
        info.addProperty("title", title);
        info.addProperty("description", description);
        placeholders.add(key, info);
        return this;
    }

    public JsonObject json(String type) {
        JsonObject result = schema.deepCopy();
        JsonObject typeField = new JsonObject(); typeField.addProperty("default", type);
        if (type.startsWith("default:")) {
            JsonArray aliases = new JsonArray(); aliases.add(type); aliases.add(type.substring(8)); typeField.add("enum", aliases);
        } else typeField.addProperty("const", type);
        result.getAsJsonObject("properties").add("type", typeField);
        return result;
    }
}
