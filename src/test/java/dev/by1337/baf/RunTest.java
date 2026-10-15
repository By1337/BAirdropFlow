package dev.by1337.baf;

import dev.by1337.baf.component.AirdropComponents;
import dev.by1337.baf.config.WebConfig;
import dev.by1337.baf.generator.Generators;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
import dev.by1337.baf.web.AirdropWeb;
import dev.by1337.baf.workflow.WorkflowFiles;
import dev.by1337.baf.workflow.WorkflowRepository;
import dev.by1337.web.ClientList;
import dev.by1337.web.db.Database;
import dev.by1337.web.db.User;
import dev.by1337.web.network.ConnectionListener;
import dev.by1337.web.network.content.GetStaticContentHandler;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

public class RunTest {
    private static final Logger log = LoggerFactory.getLogger(RunTest.class);

    //  @Test
    //ЭТО НЕ ТЕСТ, А ЗАПУСК WEB РЕДАКТОРА ДЛЯ ОТЛАДКИ
    void startBackand() throws Exception {
        System.setProperty("baf.ide", "true");
        MockBukkit.mock();
        Generators.boot();
        AirdropSpawnerBoot.boot();
        AirdropComponents.boot();
        Database database = new Database() {
            public boolean isValidSecret(String secret) {
                return false;
            }

            public User getUserByLogin(String login) {
                return null;
            }

            public boolean addUser(User user) {
                throw new UnsupportedOperationException();
            }

            public void close() {
            }
        };
        var clients = new ClientList();
        var server = new ConnectionListener(clients, new GetStaticContentHandler(clients, Path.of("/home/by1337/Projects/src_java/BAirdropFlow/web/dist/")), database);
        var port = server.startServerListener(0);
        WorkflowFiles workflowFiles = new WorkflowFiles(Path.of("/home/by1337/Projects/src_java/BAirdropFlow/src/test/resources/workflow"), Path.of("/home/by1337/Projects/src_java/BAirdropFlow/src/test/resources/backups"));
        WorkflowRepository repository = new WorkflowRepository(workflowFiles, Workflow.CODEC, log::error);
        AirdropWeb web = new AirdropWeb(repository, new WebConfig(
                "ws://localhost:" + port + "/api/ws",
                //"bairdropflow",
                "",
                ""
        ), Executors.newSingleThreadExecutor());
        System.out.println("Preview: " + web.link().get());
        new CountDownLatch(1).await(); // Ctrl+C stops the preview process.*/
    }
}
