package dev.by1337.baf.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.config.WebConfig;
import dev.by1337.baf.workflow.WorkflowFiles;
import dev.by1337.baf.workflow.WorkflowRepository;
import dev.by1337.web.client.RequestRouter;
import dev.by1337.web.client.WebEndpoint;

import java.io.Closeable;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class AirdropWeb implements Closeable {
    private final WebEndpoint endpoint;
    private final WebConfig config;

    public AirdropWeb(WorkflowRepository workflows, WebConfig config, Executor executor) {
        this.config = config;
        endpoint = new WebEndpoint(router(workflows, () -> JsonValues.GSON.toJsonTree(AirdropComponent.REGISTRY.schemas()), executor),
                false, config.url(), config.staticContent(), config.secret().isBlank() ? null : config.secret(), config.description());
    }

    public CompletableFuture<String> link() {
        return endpoint.connect().thenApply(conn -> {
            if (conn == null) throw new IllegalStateException("Не удалось подключиться к BWebTunnel");
            URI uri = URI.create(config.url());
            String scheme = uri.getScheme().equals("wss") ? "https" : "http";
            return scheme + "://" + uri.getRawAuthority() + "/" + conn.getToken() + "/" + config.staticContent() + "/";
        });
    }

    public static RequestRouter router(WorkflowRepository workflows, Supplier<JsonElement> schemas, Executor executor) {
        return new RequestRouter()
                .get("/airdrops", h -> dispatch(h, executor, () -> workflows.list()))
                .get("/schemas", h -> dispatch(h, executor, schemas::get))
                .get("/airdrop", h -> dispatch(h, executor, () -> workflows.read(h.params().getString("key"))))
                .post("/commands/parse", h -> dispatch(h, executor, () -> {
                    JsonObject body = JsonParser.parseString(h.params().body()).getAsJsonObject();
                    if (!body.has("yaml") || !body.get("yaml").isJsonPrimitive())
                        throw new IllegalArgumentException("Нужен текст YAML");
                    return CommandYaml.parse(body.get("yaml").getAsString());
                }))
                .post("/airdrop", h -> dispatch(h, executor, () -> {
                    if (!h.params().contentType().split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
                        throw new IllegalArgumentException("Ожидалось application/json");
                    }
                    JsonObject body = JsonParser.parseString(h.params().body()).getAsJsonObject();
                    if (!body.has("key") || !body.has("revision") || !body.has("nodes") || !body.has("metadata")) {
                        throw new IllegalArgumentException("Нужны key, revision, nodes, metadata");
                    }
                    return workflows.save(body.get("key").getAsString(), body.get("revision").getAsString(),
                            body.getAsJsonObject("nodes"), body.getAsJsonObject("metadata"), body.getAsJsonObject("settings"));
                }));
    }

    private static void dispatch(RequestRouter.Handler handler, Executor executor, Request action) {
        try {
            executor.execute(() -> {
                JsonObject response = new JsonObject();
                try {
                    JsonElement data = action.get();
                    JsonValues.raw(data);
                    response.addProperty("ok", true);
                    response.add("data", data);
                    if (JsonValues.GSON.toJson(response).getBytes(java.nio.charset.StandardCharsets.UTF_8).length >
                            dev.by1337.web.client.WebProtocol.MAX_PAYLOAD_SIZE) {
                        throw new IllegalArgumentException("Ответ превышает лимит туннеля 1 MiB");
                    }
                } catch (Exception e) {
                    response = new JsonObject();
                    response.addProperty("ok", false);
                    response.addProperty("code", e instanceof WorkflowFiles.ConflictException ? "conflict" : "invalid");
                    response.addProperty("error", e.getMessage() == null ? "Не удалось обработать запрос" : e.getMessage());
                }
                JsonValues.write(handler.writer(), null, response);
                handler.send();
            });
        } catch (RuntimeException e) {
            try (var ignored = handler.writer().startObject(null)) {
                handler.writer().putBoolean("ok", false);
                handler.writer().putString("error", "Плагин останавливается");
            }
            handler.send();
        }
    }

    @FunctionalInterface
    private interface Request {
        JsonElement get() throws Exception;
    }

    @Override
    public void close() {
        endpoint.close();
    }
}
