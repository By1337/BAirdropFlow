package dev.by1337.baf.addon;

import dev.by1337.baf.BAirdropFlow;
import dev.by1337.baf.Workflow;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.component.AirdropComponents;
import dev.by1337.baf.generator.Generator;
import dev.by1337.baf.generator.Generators;
import dev.by1337.baf.spawner.AirdropSpawner;
import dev.by1337.baf.spawner.AirdropSpawnerBoot;
import dev.by1337.baf.spawner.global.GlobalSpawner;
import dev.by1337.yaml.YamlValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.slf4j.LoggerFactory;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AddonLoaderTest {
    @TempDir Path directory;
    private BAirdropFlow plugin;

    @BeforeEach void boot() {
        MockBukkit.mock();
        plugin = mock(BAirdropFlow.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getSLF4JLogger()).thenReturn(LoggerFactory.getLogger("AddonLoaderTest"));
        bootRegistries();
    }

    private void bootRegistries() {
        Generators.boot();
        AirdropSpawnerBoot.boot();
        AirdropComponents.boot();
    }

    @AfterEach void cleanup() {
        try { bootRegistries(); }
        finally { MockBukkit.unmock(); }
    }

    @Test void jarHooksRegisterTypesBeforeDecodingAndReloadWithFreshClasses() throws Exception {
        createAddon("Example", "example", false);
        ClassLoader oldLoader;
        try (var loader = new AddonLoader(plugin, directory.resolve("addons"))) {
            loader.findAddons();
            assertEquals(1, loader.addons().size());
            var addon = loader.addons().getFirst();
            oldLoader = addon.getClass().getClassLoader();
            assertNotSame(getClass().getClassLoader(), oldLoader);
            assertFalse(Files.exists(directory.resolve("example.log")));
            assertSame(plugin, addon.getPlugin());
            try (var resource = addon.getResource("config.yml")) {
                assertNotNull(resource);
                assertEquals("addon resource", new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            loader.registerAll();
            assertEquals("load\ncomponents\ngenerators\nspawners\nglobal\n", Files.readString(directory.resolve("example.log")));
            var workflow = Workflow.CODEC.decode(YamlValue.wrap(Map.of(
                    "id", "test:event", "nodes", Map.of("__init__", Map.of("type", "test:init")),
                    "generator", Map.of("type", "test:generator"),
                    "spawner", Map.of("type", "test:spawner", "time", "1s", "when", Map.of())))).getOrThrow();
            assertEquals("test:event", workflow.id());
            assertNotNull(GlobalSpawner.REGISTRY.decoder.decode(YamlValue.wrap(Map.of(
                    "type", "test:global", "events", Map.of("13:00", "test:event")))).getOrThrow());
            assertTrue(AirdropComponent.REGISTRY.schemas().containsKey("test:init"));
            assertTrue(Generators.schemas().containsKey("test:generator"));
            assertTrue(AirdropSpawnerBoot.schemas().containsKey("test:spawner"));
            var schemas = Generators.schemas();
            schemas.get("test:generator").addProperty("mutated", true);
            assertFalse(Generators.schemas().get("test:generator").has("mutated"));
            loader.enableAll();
            assertTrue(Files.readString(directory.resolve("example.log")).endsWith("enable\n"));
        }
        assertTrue(Files.readString(directory.resolve("example.log")).endsWith("disable\n"));
        bootRegistries();
        assertNull(Generator.REGISTRY.get("test:generator"));
        assertFalse(Generators.schemas().containsKey("test:generator"));
        try (var loader = new AddonLoader(plugin, directory.resolve("addons"))) {
            loader.findAddons();
            assertNotSame(oldLoader, loader.addons().getFirst().getClass().getClassLoader());
            loader.registerAll();
            assertNotNull(Generator.REGISTRY.get("test:generator"));
        }
    }

    @Test void brokenAndDuplicateJarsAreSkippedWithoutRunningHooks() throws Exception {
        createAddon("First", "same", false);
        createAddon("Second", "same", false);
        Files.writeString(directory.resolve("addons/broken.jar"), "not a jar");
        try (var loader = new AddonLoader(plugin, directory.resolve("addons"))) {
            loader.findAddons();
            assertEquals(1, loader.addons().size());
            assertFalse(Files.exists(directory.resolve("same.log")));
            loader.registerAll();
        }
        assertEquals(1, Files.readString(directory.resolve("same.log")).lines().filter("disable"::equals).count());
    }

    @Test void failedRegistrationStopsStartupAndStillDisablesAddonOnce() throws Exception {
        createAddon("Failure", "failure", true);
        var loader = new AddonLoader(plugin, directory.resolve("addons"));
        try {
            loader.findAddons();
            var error = assertThrows(IllegalStateException.class, loader::registerAll);
            assertTrue(error.getMessage().contains("failure"));
        } finally {
            loader.close();
            loader.close();
        }
        assertEquals("load\ncomponents\ndisable\n", Files.readString(directory.resolve("failure.log")));
        assertThrows(IllegalStateException.class, loader::enableAll);
    }

    @Test void failedEnableStillDisablesAddonOnce() throws Exception {
        createAddon("EnableFailure", "enable_failure", false, true);
        try (var loader = new AddonLoader(plugin, directory.resolve("addons"))) {
            loader.findAddons();
            loader.registerAll();
            assertThrows(IllegalStateException.class, loader::enableAll);
        }
        assertTrue(Files.readString(directory.resolve("enable_failure.log")).endsWith("enable\ndisable\n"));
    }

    private void createAddon(String className, String name, boolean fail) throws Exception {
        createAddon(className, name, fail, false);
    }

    private void createAddon(String className, String name, boolean fail, boolean failEnable) throws Exception {
        Path work = Files.createDirectories(directory.resolve(className));
        Path source = work.resolve(className + ".java");
        Files.writeString(source, """
                package fixture;
                import dev.by1337.baf.api.addon.*;
                import dev.by1337.baf.component.*;
                import dev.by1337.baf.component.impl.InitComponent;
                import dev.by1337.baf.generator.*;
                import dev.by1337.baf.generator.impl.FixedGenerator;
                import dev.by1337.baf.registry.*;
                import dev.by1337.baf.spawner.*;
                import dev.by1337.baf.spawner.impl.TimerSpawner;
                import dev.by1337.baf.spawner.global.*;
                @BAFAddon(name = "%s")
                public class %s extends AbstractAddon {
                    private void record(String phase) {
                        try {
                            java.nio.file.Files.writeString(getPlugin().getDataFolder().toPath().resolve("%s.log"),
                                phase + "\\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                        } catch (java.io.IOException e) { throw new RuntimeException(e); }
                    }
                    public void onLoad() { record("load"); }
                    public void componentsRegister(NodeRegistry r) {
                        record("components");
                        if (%s) throw new IllegalStateException("registration failed");
                        r.register("test:init", InitComponent.Config.DECODER, InitComponent.Config.WEB_SCHEMA);
                    }
                    public void generatorsRegister(SchemaRegistry<Generator> r) {
                        record("generators"); r.register("test:generator", FixedGenerator.DECODER, FixedGenerator.WEB_SCHEMA);
                    }
                    public void spawnersRegister(SchemaRegistry<AirdropSpawner> r) {
                        record("spawners"); r.register("test:spawner", TimerSpawner.DECODER, TimerSpawner.WEB_SCHEMA);
                    }
                    public void globalSpawnersRegister(DecoderRegistry<GlobalSpawner> r) {
                        record("global"); r.register("test:global", ByTimeSpawner.DECODER);
                    }
                    public void onEnable() {
                        record("enable");
                        if (%s) throw new IllegalStateException("enable failed");
                    }
                    public void onDisable() { record("disable"); }
                }
                """.formatted(name, className, name, fail, failEnable));
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Tests require a JDK");
        assertEquals(0, compiler.run(null, null, null, "-proc:none", "-classpath", System.getProperty("java.class.path"),
                "-d", work.toString(), source.toString()));
        Path addons = Files.createDirectories(directory.resolve("addons"));
        try (var out = new JarOutputStream(Files.newOutputStream(addons.resolve(className + ".jar")))) {
            out.putNextEntry(new JarEntry("fixture/" + className + ".class"));
            Files.copy(work.resolve("fixture/" + className + ".class"), out);
            out.closeEntry();
            out.putNextEntry(new JarEntry("config.yml"));
            out.write("addon resource".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
