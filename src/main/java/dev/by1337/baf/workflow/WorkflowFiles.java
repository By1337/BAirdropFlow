package dev.by1337.baf.workflow;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.by1337.baf.web.JsonValues;
import dev.by1337.yaml.YamlMap;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Only replaces editor-owned fields after validation and a byte-for-byte backup.
 */
public final class WorkflowFiles {
    private static final Set<String> SETTINGS = Set.of("id", "world", "args", "generator", "spawner", "defaults");
    private final Path home;
    private final Path backups;

    public WorkflowFiles(Path home, Path backups) {
        this.home = home.toAbsolutePath().normalize();
        this.backups = backups.toAbsolutePath().normalize();
    }

    public synchronized List<String> keys() throws IOException {
        Files.createDirectories(home);
        try (var files = Files.walk(home)) {
            return files.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .filter(p -> {
                        try {
                            return p.toRealPath().startsWith(home.toRealPath());
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .map(p -> home.relativize(p).toString().replace('\\', '/')).sorted().toList();
        }
    }

    private Path resolve(String key) throws IOException {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Не указан файл аирдропа");
        Path path = home.resolve(key).normalize();
        if (!path.startsWith(home) || !Files.isRegularFile(path) || !path.toRealPath().startsWith(home.toRealPath())) {
            throw new IllegalArgumentException("Аирдроп не найден");
        }
        if (!key.endsWith(".yml") && !key.endsWith(".yaml")) throw new IllegalArgumentException("Ожидался YAML-файл");
        return path;
    }

    public synchronized Document read(String key) throws IOException {
        Path path = resolve(key);
        byte[] original = Files.readAllBytes(path);
        YamlMap yaml = YamlMap.loadFromString(new String(original, java.nio.charset.StandardCharsets.UTF_8));
        return new Document(key, revision(original), yaml);
    }

    public synchronized Document save(String key, String revision, JsonObject nodes, JsonObject metadata,
                                      JsonObject settings, Consumer<YamlMap> validator) throws IOException {
        Path path = resolve(key);
        byte[] original = Files.readAllBytes(path);
        YamlMap candidate = candidate(original, revision, nodes, metadata, settings, validator);
        byte[] bytes = candidate.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Document result = new Document(key, revision(bytes), candidate);
        if (JsonValues.GSON.toJson(result.json()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length >
                dev.by1337.web.client.WebProtocol.MAX_PAYLOAD_SIZE - 64) {
            throw new IllegalArgumentException("Конфиг превышает лимит ответа туннеля 1 MiB");
        }
        if (!revision(Files.readAllBytes(path)).equals(revision)) throw new ConflictException();
        Path backup = backups.resolve(System.currentTimeMillis() + "-" + UUID.randomUUID()).resolve(home.relativize(path));
        Files.createDirectories(backup.getParent());
        Files.write(backup, original, StandardOpenOption.CREATE_NEW);
        Path temporary = Files.createTempFile(path.getParent(), ".airdrop-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        return result;
    }

    private YamlMap candidate(byte[] original, String revision, JsonObject nodes, JsonObject metadata,
                              JsonObject settings, Consumer<YamlMap> validator) {
        if (revision == null || !revision(original).equals(revision)) throw new ConflictException();
        YamlMap candidate = YamlMap.loadFromString(new String(original, java.nio.charset.StandardCharsets.UTF_8));
        candidate.set("nodes", null);
        candidate.set("nodes", JsonValues.raw(nodes));
        candidate.set("$web_meta", JsonValues.GSON.toJson(metadata));
        if (settings != null) {
            for (var entry : settings.entrySet()) {
                if (!SETTINGS.contains(entry.getKey()))
                    throw new IllegalArgumentException("Нельзя менять поле " + entry.getKey());
                candidate.set(entry.getKey(), JsonValues.raw(entry.getValue()));
            }
        }
        validator.accept(candidate);
        return candidate;
    }

    private static String revision(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record Document(String key, String revision, YamlMap yaml) {
        public JsonObject json() {
            JsonObject result = new JsonObject();
            result.addProperty("key", key);
            result.addProperty("revision", revision);
            result.add("config", JsonValues.GSON.toJsonTree(yaml.getRaw()));
            Object rawMeta = yaml.getRaw("$web_meta");
            JsonElement meta;
            try {
                meta = rawMeta instanceof String s ? JsonParser.parseString(s) : JsonValues.GSON.toJsonTree(rawMeta);
            } catch (RuntimeException e) {
                meta = new JsonObject();
            }
            result.add("metadata", meta != null && meta.isJsonObject() ? meta : new JsonObject());
            return result;
        }
    }

    public static final class ConflictException extends IllegalStateException {
        public ConflictException() {
            super("Конфиг изменился. Перезагрузите его перед сохранением.");
        }
    }
}
