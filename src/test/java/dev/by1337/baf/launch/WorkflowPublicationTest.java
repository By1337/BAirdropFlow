package dev.by1337.baf.launch;

import dev.by1337.baf.Workflow;
import dev.by1337.baf.component.AirdropComponents;
import dev.by1337.baf.generator.Generators;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
import dev.by1337.baf.workflow.WorkflowFiles;
import dev.by1337.baf.workflow.WorkflowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowPublicationTest {
    @TempDir Path directory;
    @Test void fileAndEditorUpdatesPublishNewIdsRemoveInvalidFilesAndRejectVoteIdCollisions() throws Exception {
        MockBukkit.mock();
        try {
            Generators.boot(); AirdropSpawnerBoot.boot(); AirdropComponents.boot();
            var registry = new LaunchRegistry();
            var repo = new WorkflowRepository(new WorkflowFiles(directory.resolve("workflow"), directory.resolve("backups")),
                    Workflow.CODEC, ignored -> {});
            repo.bindRegistry(registry);
            var document = repo.create("one.yml", "event:one", "world");
            assertSame(repo.get("one.yml"), registry.get("event:one"));
            var settings = new com.google.gson.JsonObject();
            settings.addProperty("id", "event:renamed");
            repo.save("one.yml", document.get("revision").getAsString(), document.getAsJsonObject("config").getAsJsonObject("nodes"),
                    document.getAsJsonObject("metadata"), settings);
            assertNull(registry.get("event:one")); assertSame(repo.get("one.yml"), registry.get("event:renamed"));
            registry.publish("vote", "event:vote", () -> {});
            assertThrows(IllegalArgumentException.class, () -> repo.create("duplicate.yml", "event:vote", "world"));
            assertFalse(Files.exists(directory.resolve("workflow/duplicate.yml")));
            Files.writeString(directory.resolve("workflow/one.yml"), "id: broken\nnodes: {}\n");
            repo.read("one.yml"); assertNull(registry.get("event:renamed"));
            repo.create("two.yml", "event:two", "world");
            assertNotNull(registry.get("event:two"));
            Files.delete(directory.resolve("workflow/two.yml"));
            repo.list(); assertNull(registry.get("event:two")); assertNotNull(registry.get("event:vote"));
        } finally { MockBukkit.unmock(); }
    }
}
