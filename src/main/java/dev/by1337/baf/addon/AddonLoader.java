package dev.by1337.baf.addon;

import dev.by1337.baf.BAirdropFlow;
import dev.by1337.baf.api.addon.AbstractAddon;
import dev.by1337.baf.api.addon.AddonDescription;
import dev.by1337.baf.api.addon.BAFAddon;
import dev.by1337.baf.component.AirdropComponent;
import dev.by1337.baf.generator.Generator;
import dev.by1337.baf.spawner.AirdropSpawner;
import dev.by1337.baf.spawner.global.GlobalSpawner;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;

public final class AddonLoader implements Closeable {
    private record Loaded(AbstractAddon addon, URLClassLoader loader) {}

    private final BAirdropFlow plugin;
    private final Path directory;
    private final List<Loaded> loaded = new ArrayList<>();
    private final List<AbstractAddon> started = new ArrayList<>();
    private boolean discovered;
    private boolean registrationAttempted;
    private boolean registered;
    private boolean enabled;
    private boolean closed;

    public AddonLoader(BAirdropFlow plugin, Path directory) {
        this.plugin = plugin;
        this.directory = directory;
    }

    public List<AbstractAddon> addons() {
        return loaded.stream().map(Loaded::addon).toList();
    }

    public void findAddons() throws IOException {
        if (discovered || closed) throw new IllegalStateException("Addon discovery already finished");
        discovered = true;
        Files.createDirectories(directory);
        Set<String> names = new HashSet<>();
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jar")).sorted().toList()) {
                AddonClassLoader loader = new AddonClassLoader(file, getClass().getClassLoader());
                try {
                    Class<? extends AbstractAddon> main = findMain(file, loader);
                    BAFAddon annotation = main.getAnnotation(BAFAddon.class);
                    String name = annotation.name();
                    if (!name.matches("[A-Za-z0-9_-]+"))
                        throw new IllegalArgumentException("Invalid addon name " + name);
                    if (!names.add(name.toLowerCase(java.util.Locale.ROOT)))
                        throw new IllegalArgumentException("Duplicate addon name " + name);
                    AbstractAddon addon = main.getConstructor().newInstance();
                    addon.initialize(plugin, new AddonDescription(name, annotation.version(), annotation.author(), main.getName()));
                    loaded.add(new Loaded(addon, loader));
                } catch (IOException | ReflectiveOperationException | RuntimeException | LinkageError error) {
                    try {
                        loader.close();
                    } catch (IOException closeError) {
                        error.addSuppressed(closeError);
                    }
                    plugin.getSLF4JLogger().error("Failed to discover addon {}", file.getFileName(), error);
                }
            }
        }
    }

    private static Class<? extends AbstractAddon> findMain(Path file, ClassLoader loader)
            throws IOException, ClassNotFoundException {
        Class<? extends AbstractAddon> main = null;
        try (JarFile jar = new JarFile(file.toFile())) {
            for (var entry : Collections.list(jar.entries())) {
                String path = entry.getName();
                if (!path.endsWith(".class") || path.startsWith("META-INF/") || path.equals("module-info.class"))
                    continue;
                Class<?> candidate;
                try {
                    candidate = Class.forName(path.substring(0, path.length() - 6).replace('/', '.'), false, loader);
                } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
                    continue;
                }
                if (candidate.getClassLoader() != loader || !candidate.isAnnotationPresent(BAFAddon.class)) continue;
                if (main != null) throw new IllegalArgumentException("Multiple @BAFAddon entry points");
                main = candidate.asSubclass(AbstractAddon.class);
            }
        }
        if (main == null) throw new IllegalArgumentException("No @BAFAddon entry point");
        return main;
    }

    public void registerAll() {
        if (!discovered || registrationAttempted || closed)
            throw new IllegalStateException("Invalid addon registration phase");
        registrationAttempted = true;
        for (Loaded entry : loaded) {
            AbstractAddon addon = entry.addon;
            started.add(addon);
            try {
                addon.onLoad();
                addon.componentsRegister(AirdropComponent.REGISTRY);
                addon.generatorsRegister(Generator.REGISTRY);
                addon.spawnersRegister(AirdropSpawner.REGISTRY);
                addon.globalSpawnersRegister(GlobalSpawner.REGISTRY);
            } catch (RuntimeException | LinkageError error) {
                throw new IllegalStateException("Failed to register addon " + addon.getDescription().name(), error);
            }
        }
        registered = true;
    }

    public void enableAll() {
        if (!registered || enabled || closed) throw new IllegalStateException("Invalid addon enable phase");
        enabled = true;
        for (Loaded entry : loaded) {
            try {
                entry.addon.onEnable();
            } catch (RuntimeException | LinkageError error) {
                throw new IllegalStateException("Failed to enable addon " + entry.addon.getDescription().name(), error);
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (int i = started.size() - 1; i >= 0; i--) {
            AbstractAddon addon = started.get(i);
            try {
                addon.onDisable();
            } catch (RuntimeException | LinkageError error) {
                plugin.getSLF4JLogger().error("Failed to disable addon {}", addon.getDescription().name(), error);
            }
        }
        started.clear();
        for (Loaded entry : loaded) {
            try {
                entry.loader.close();
            } catch (IOException error) {
                plugin.getSLF4JLogger().error("Failed to close addon {}", entry.addon.getDescription().name(), error);
            }
        }
        loaded.clear();
    }

    private static final class AddonClassLoader extends URLClassLoader {
        private AddonClassLoader(Path file, ClassLoader parent) throws IOException {
            super(new URL[]{file.toUri().toURL()}, parent);
        }

        @Override
        public URL getResource(String name) {
            return findResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return findResources(name);
        }
    }
}
