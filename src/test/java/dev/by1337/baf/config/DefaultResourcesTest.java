package dev.by1337.baf.config;

import dev.by1337.yaml.YamlMap;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultResourcesTest {
    @TempDir Path directory;

    private Plugin plugin() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getResource(anyString())).thenAnswer(call ->
                getClass().getClassLoader().getResourceAsStream(call.getArgument(0)));
        doAnswer(call -> {
            String resource = call.getArgument(0);
            assertFalse((boolean) call.getArgument(1));
            try (var input = plugin.getResource(resource)) {
                assertNotNull(input, resource);
                Path destination = directory.resolve(resource);
                Files.createDirectories(destination.getParent());
                Files.copy(input, destination);
            }
            return null;
        }).when(plugin).saveResource(anyString(), anyBoolean());
        return plugin;
    }

    @Test void savesAllDeclaredDefaultsIncludingBinarySchematics() throws Exception {
        DefaultResources.save(plugin());
        try (var input = getClass().getClassLoader().getResourceAsStream("default-resources.yml")) {
            var manifest = YamlMap.load(input);
            var folders = dev.by1337.yaml.decoder.YamlDecoder.mapOf(
                    dev.by1337.yaml.decoder.YamlDecoder.STRING,
                    dev.by1337.yaml.decoder.YamlDecoder.STRING.listOf())
                    .decode(manifest.get("folders")).getOrThrow();
            for (var folder : folders.entrySet()) {
                for (String name : folder.getValue()) {
                    String resource = folder.getKey() + "/" + name;
                    try (var original = getClass().getClassLoader().getResourceAsStream(resource)) {
                        assertNotNull(original, resource);
                        assertArrayEquals(original.readAllBytes(), Files.readAllBytes(directory.resolve(resource)), resource);
                    }
                }
            }
        }
        assertTrue(Files.size(directory.resolve("loot.yml")) > 0);
    }

    @Test void existingFoldersAndRootFilesAreUntouched() throws Exception {
        Files.createDirectory(directory.resolve("workflow"));
        Files.writeString(directory.resolve("workflow/custom.yml"), "custom");
        Files.createDirectory(directory.resolve("menu"));
        Files.writeString(directory.resolve("loot.yml"), "custom loot");
        Plugin plugin = plugin();
        DefaultResources.save(plugin);
        assertEquals("custom", Files.readString(directory.resolve("workflow/custom.yml")));
        assertFalse(Files.exists(directory.resolve("workflow/airdrop.yml")));
        assertFalse(Files.exists(directory.resolve("menu/vote.yml")));
        assertEquals("custom loot", Files.readString(directory.resolve("loot.yml")));
        assertTrue(Files.exists(directory.resolve("spawner/vote.yml")));

        Files.delete(directory.resolve("spawner/vote.yml"));
        DefaultResources.save(plugin);
        assertFalse(Files.exists(directory.resolve("spawner/vote.yml")));
    }
}
