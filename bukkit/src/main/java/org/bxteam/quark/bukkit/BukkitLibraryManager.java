package org.bxteam.quark.bukkit;

import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.bxteam.quark.classloader.IsolatedClassLoaderImpl;
import org.bxteam.quark.classloader.URLClassLoaderHelper;
import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.logger.LogLevel;
import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.logger.adapters.JavaLogAdapter;
import org.bxteam.quark.relocation.Relocation;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Bukkit-specific implementation of LibraryManager.
 */
public class BukkitLibraryManager extends LibraryManager {
    private final JavaPlugin plugin;
    private final URLClassLoaderHelper classLoaderHelper;

    public BukkitLibraryManager(@NotNull JavaPlugin plugin) {
        this(plugin, "libs");
    }

    public BukkitLibraryManager(@NotNull JavaPlugin plugin, @NotNull String librariesDirectoryName) {
        this(plugin, librariesDirectoryName, new JavaLogAdapter(plugin.getLogger()));
    }

    public BukkitLibraryManager(@NotNull JavaPlugin plugin, @NotNull String librariesDirectoryName, @NotNull LogAdapter logAdapter) {
        super(logAdapter, plugin.getDataFolder().toPath(), librariesDirectoryName);

        this.plugin = requireNonNull(plugin, "Plugin cannot be null");

        ClassLoader pluginClassLoader = plugin.getClass().getClassLoader();
        if (!(pluginClassLoader instanceof URLClassLoader)) {
            throw new IllegalStateException("Plugin class loader is not a URLClassLoader: " + pluginClassLoader.getClass());
        }

        this.classLoaderHelper = new URLClassLoaderHelper((URLClassLoader) pluginClassLoader, this);

        logger.debug("Initialized BukkitLibraryManager for plugin: " + plugin.getName());
    }

    @Override
    protected void addToClasspath(@NotNull Path jarPath) {
        requireNonNull(jarPath, "JAR path cannot be null");

        try {
            classLoaderHelper.addToClasspath(jarPath);
        } catch (Exception e) {
            throw new LibraryLoadException("Failed to add JAR to plugin classpath: " + jarPath, e);
        }
    }

    @Override
    @NotNull
    protected IsolatedClassLoader createIsolatedClassLoader() {
        return new IsolatedClassLoaderImpl();
    }

    @Override
    @Nullable
    protected InputStream getResourceAsStream(@NotNull String resourcePath) {
        requireNonNull(resourcePath, "Resource path cannot be null");
        return plugin.getResource(resourcePath);
    }

    /**
     * Loads a dependency by Maven coordinates.
     */
    public void loadDependency(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
        requireNonNull(groupId, "Group ID cannot be null");
        requireNonNull(artifactId, "Artifact ID cannot be null");
        requireNonNull(version, "Version cannot be null");

        Dependency dependency = Dependency.of(groupId, artifactId, version);
        loadDependency(dependency);
    }

    /**
     * Loads a single dependency.
     */
    public void loadDependency(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");
        loadDependencies(Collections.singletonList(dependency));
    }

    /**
     * Loads dependencies without relocations.
     */
    public void loadDependencies(@NotNull List<Dependency> dependencies) {
        loadDependencies(dependencies, Collections.emptyList());
    }

    /**
     * Loads dependencies with relocations.
     */
    public void loadDependencies(@NotNull List<Dependency> dependencies, @NotNull List<Relocation> relocations) {
        requireNonNull(dependencies, "Dependencies cannot be null");
        requireNonNull(relocations, "Relocations cannot be null");

        loadDependencies(new BukkitClassLoaderAdapter(classLoaderHelper), dependencies, relocations);
    }

    /**
     * Loads dependencies into an isolated class loader.
     */
    public void loadDependenciesIsolated(@NotNull IsolatedClassLoader isolatedClassLoader,
                                         @NotNull List<Dependency> dependencies,
                                         @NotNull List<Relocation> relocations) {
        super.loadDependencies(isolatedClassLoader, dependencies, relocations);
    }

    /**
     * Enables detailed logging for debugging dependency issues.
     */
    public void enableDebugLogging() {
        setLogLevel(LogLevel.DEBUG);
        logger.info("Debug logging enabled for " + plugin.getName());
    }

    /**
     * Gets the associated Bukkit plugin.
     */
    @NotNull
    public JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * Gets the plugin's class loader helper.
     */
    @NotNull
    public URLClassLoaderHelper getClassLoaderHelper() {
        return classLoaderHelper;
    }

    public boolean isPluginEnabled() {
        return plugin.isEnabled();
    }

    @NotNull
    public String getPluginName() {
        return plugin.getName();
    }

    @NotNull
    public String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String toString() {
        return "BukkitLibraryManager{" +
                "plugin=" + plugin.getName() +
                ", version=" + getPluginVersion() +
                ", repositories=" + getRepositories().size() +
                '}';
    }

    /**
     * Adapter that allows URLClassLoaderHelper to work with IsolatedClassLoader interface.
     */
    private record BukkitClassLoaderAdapter(URLClassLoaderHelper helper) implements IsolatedClassLoader {

        @Override
        public void addPath(@NotNull Path path) {
            helper.addToClasspath(path);
        }

        @Override
        @NotNull
        public Class<?> loadClass(@NotNull String className) throws ClassNotFoundException {
            throw new UnsupportedOperationException("Class loading not supported in Bukkit adapter");
        }

        @Override
        public void close() {
            // URLClassLoaderHelper doesn't need closing
        }
    }
}
