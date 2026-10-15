package dev.by1337.baf.vote;

import dev.by1337.baf.launch.LaunchRegistry;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
import dev.by1337.baf.spawner.global.GlobalSpawner;
import dev.by1337.baf.spawner.global.SpawnerContext;
import dev.by1337.bmenu.loader.MenuLoader;
import dev.by1337.bmenu.loader.v2.MenuDecoders;
import dev.by1337.yaml.YamlMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VoteConfigTest {
    @TempDir Path directory;
    @Test void shippedAndMigrationMenusDecodeWithBMenuAndMatchRegisteredOptions() throws Exception {
        MockBukkit.mock();
        try (var papi = mockStatic(me.clip.placeholderapi.PlaceholderAPIPlugin.class)) {
            var plugin = mock(me.clip.placeholderapi.PlaceholderAPIPlugin.class);
            when(plugin.getLocalExpansionManager()).thenReturn(mock(me.clip.placeholderapi.expansion.manager.LocalExpansionManager.class));
            papi.when(me.clip.placeholderapi.PlaceholderAPIPlugin::getInstance).thenReturn(plugin);
            AirdropSpawnerBoot.boot();
            // Decoder uses the real BMenu codecs; the loader only supplies provider lookup and logging.
            var loader = mock(MenuLoader.class);
            doReturn(VoteMenu.CODEC).when(loader).findMenuCodec("baf:vote");
            when(loader.logger()).thenReturn(org.slf4j.LoggerFactory.getLogger(VoteConfigTest.class));
            var pairs = new ArrayList<List<Path>>();
            Path menu = directory.resolve("vote-menu.yml"), vote = directory.resolve("vote.yml");
            Files.copy(Path.of("src/main/resources/menu/vote.yml"), menu);
            Files.copy(Path.of("src/main/resources/spawner/vote.yml"), vote);
            pairs.add(List.of(menu, vote));
            if (Files.exists(Path.of("CONFIGS/spawner/vote.yml")))
                pairs.add(List.of(Path.of("CONFIGS/menu/vote.yml"), Path.of("CONFIGS/spawner/vote.yml")));
            for (var pair : pairs) {
                var decoded = MenuDecoders.factory().apply(loader).decode(pair.getFirst().toFile());
                assertFalse(decoded.hasError(), decoded.error()); assertNotNull(decoded.result());
                var definition = VoteSpawner.DECODER.decode(YamlMap.load(pair.getLast().toFile()).get()).getOrThrow();
                var registry = new LaunchRegistry();
                definition.dependencies().forEach(id -> registry.publish(id, id, () -> {}));
                registry.publish(definition.id(), definition.id(), definition);
                var service = new VotingService(task -> {}, message -> fail(message), message -> {}, session -> {});
                definition.start(new SpawnerContext(registry, service));
                registry.start(definition.id()); assertNotNull(service.active());
                String yaml = Files.readString(pair.getFirst());
                for (String id : definition.dependencies()) assertTrue(yaml.contains("[vote] " + id), id);
                assertEquals(Set.copyOf(definition.dependencies()), definition.config().options().keySet());
                assertFalse(service.active().text("{time_left} {total_votes}", UUID.randomUUID()).contains("{"));
                service.close();
            }
        } finally { MockBukkit.unmock(); }
    }
}
