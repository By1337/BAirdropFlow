package dev.by1337.baf.web;

import dev.by1337.baf.Workflow;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.AirdropComponents;
import dev.by1337.baf.generator.Generators;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
import dev.by1337.baf.workflow.WorkflowFiles;
import dev.by1337.baf.workflow.WorkflowRepository;
import dev.by1337.web.ClientList;
import dev.by1337.web.client.WebEndpoint;
import dev.by1337.web.db.Database;
import dev.by1337.web.db.User;
import dev.by1337.web.network.ConnectionListener;
import dev.by1337.web.network.content.GetStaticContentHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class EditorDevServer implements AutoCloseable {
    public static final String EXAMPLE = """
            id: demo:default
            world: world
            generator:
              type: generator:fixed
              at: '0;100;0'
            spawner:
              type: spawner:timer
              time: 5m
              when: {}
            args:
              loot_table: example/default
            custom_field:
              preserved: true
            nodes:
              __init__:
                type: __init__
                on_init:
                  - create: opening
              opening:
                type: tick:timer
                timer: 30
                on_create:
                  - create: hologram
                on_tick:
                  - call: update hologram
                on_timer_end:
                  - create: lifetime
                on_remove:
                  - remove: hologram
              hologram:
                type: dh:hologram
                offsets: '0.5;1.8;0.5'
                lines:
                  - 'До открытия: {ctx:opening.timer}'
              lifetime:
                type: tick:timer
                timer: 60
                on_create:
                  - create: looter
                on_remove:
                  - remove: looter
                  - destroy: ''
              looter:
                type: loot:looter
                loot_table: '{args:loot_table}'
                title: '&aАирдроп'
              __destroy__:
                type: __destroy__
                on_destroy: []
            """;

    public final WorkflowRepository workflows;
    public final int port;
    public final String token;
    private final ConnectionListener server;
    private final WebEndpoint endpoint;

    public EditorDevServer(Path data, Path staticRoot, int requestedPort) throws Exception {
        Generators.boot();
        AirdropSpawnerBoot.boot();
        AirdropComponents.boot();
        Path home = data.resolve("workflow");
        Files.createDirectories(home);
        if (!Files.exists(home.resolve("default.yml"))) Files.writeString(home.resolve("default.yml"), EXAMPLE);
        workflows = new WorkflowRepository(new WorkflowFiles(home, data.resolve("backups")), Workflow.CODEC, System.err::println);
        workflows.load();
        ClientList clients = new ClientList();
        server = new ConnectionListener(clients, new GetStaticContentHandler(clients, staticRoot), new Database() {
            public boolean isValidSecret(String secret) {
                return false;
            }

            public User getUserByLogin(String login) {
                return null;
            }

            public boolean addUser(User user) {
                return false;
            }

            public void close() {
            }
        });
        port = server.startServerListener(requestedPort);
        endpoint = new WebEndpoint(AirdropWeb.router(workflows,
                () -> JsonValues.GSON.toJsonTree(AirdropComponent.REGISTRY.schemas()), Runnable::run),
                false, "ws://127.0.0.1:" + port + "/api/ws", "bairdropflow", null, null);
        var connection = endpoint.connect().get(10, TimeUnit.SECONDS);
        if (connection == null) {
            close();
            throw new IllegalStateException("Tunnel did not connect");
        }
        token = connection.getToken();
    }

    public String origin() {
        return "http://127.0.0.1:" + port;
    }

    public String link() {
        return origin() + "/" + token + "/bairdropflow/";
    }

    @Override
    public void close() {
        endpoint.close();
        server.stop();
    }

    public static void main(String[] args) throws Exception {
        Path data = args.length > 0 ? Path.of(args[0]) : Files.createTempDirectory("baf-editor-");
        Path staticRoot = args.length > 1 ? Path.of(args[1]) : Path.of("web/dist");
        int port = args.length > 2 ? Integer.parseInt(args[2]) : 18083;
        EditorDevServer server = new EditorDevServer(data, staticRoot, port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        System.out.println("EDITOR_URL=" + server.link());
        System.out.println("EDITOR_DATA=" + data.toAbsolutePath());
        new CountDownLatch(1).await();
    }
}
