package org.bxteam.quark.bukkit;

import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.bxteam.quark.classloader.IsolatedClassLoaderImpl;
import org.bxteam.quark.classloader.URLClassLoaderHelper;
import org.bxteam.quark.dependency.Dependency;
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
 * Bukkit-specific implementation of LibraryManager for Bukkit/Spigot plugins.
 *
 * <p>This implementation uses reflection to inject dependencies into the plugin's
 * URLClassLoader at runtime. It works with Bukkit, Spigot, Paper (when using
 * plugin.yml), and other Bukkit-compatible servers.</p>
 *
 * @apiNote For use in Paper plugins, consider using <b>quark-paper</b> module instead.
 */
public class BukkitLibraryManager extends LibraryManager {
    private final JavaPlugin plugin;
    private final URLClassLoaderHelper classLoaderHelper;

    /**
     * Creates a new Bukkit library manager with default settings.
     *
     * @param plugin the Bukkit plugin instance
     * @throws NullPointerException if plugin is null
     * @throws IllegalStateException if plugin class loader is not a URLClassLoader
     */
    public BukkitLibraryManager(@NotNull JavaPlugin plugin) {
        this(plugin, "libs");
    }

    /**
     * Creates a new Bukkit library manager with custom directory name.
     *
     * @param plugin the Bukkit plugin instance
     * @param librariesDirectoryName the name of the directory to store downloaded libraries
     * @throws NullPointerException if any parameter is null
     * @throws IllegalStateException if plugin class loader is not a URLClassLoader
     */
    public BukkitLibraryManager(@NotNull JavaPlugin plugin, @NotNull String librariesDirectoryName) {
        this(plugin, librariesDirectoryName, new JavaLogAdapter(plugin.getLogger()));
    }

    /**
     * Creates a new Bukkit library manager with custom settings.
     *
     * @param plugin the Bukkit plugin instance
     * @param librariesDirectoryName the name of the directory to store downloaded libraries
     * @param logAdapter the log adapter to use for logging
     * @throws NullPointerException if any parameter is null
     * @throws IllegalStateException if plugin class loader is not a URLClassLoader
     */
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
     *
     * @param groupId the Maven group ID
     * @param artifactId the Maven artifact ID
     * @param version the dependency version
     * @throws NullPointerException if any parameter is null
     */
    public void loadDependency(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
        requireNonNull(groupId, "Group ID cannot be null");
        requireNonNull(artifactId, "Artifact ID cannot be null");
        requireNonNull(version, "Version cannot be null");

        loadDependency(Dependency.of(groupId, artifactId, version));
    }

    /**
     * Loads a single dependency.
     *
     * @param dependency the dependency to load
     * @throws NullPointerException if dependency is null
     */
    public void loadDependency(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");
        loadDependencies(Collections.singletonList(dependency));
    }

    /**
     * Loads dependencies without relocations.
     *
     * @param dependencies the list of dependencies to load
     * @throws NullPointerException if dependencies is null
     */
    public void loadDependencies(@NotNull List<Dependency> dependencies) {
        loadDependencies(dependencies, Collections.emptyList());
    }

    /**
     * Loads dependencies with relocations into the main plugin classpath.
     *
     * @param dependencies the list of dependencies to load
     * @param relocations the list of relocations to apply
     * @throws NullPointerException if any parameter is null
     */
    public void loadDependencies(@NotNull List<Dependency> dependencies, @NotNull List<Relocation> relocations) {
        requireNonNull(dependencies, "Dependencies cannot be null");
        requireNonNull(relocations, "Relocations cannot be null");

        super.loadDependencies(dependencies, relocations);
    }

    /**
     * Loads dependencies into an isolated class loader.
     *
     * @param isolatedClassLoader the isolated class loader
     * @param dependencies the list of dependencies to load
     * @param relocations the list of relocations to apply
     * @throws NullPointerException if any parameter is null
     */
    public void loadDependenciesIsolated(@NotNull IsolatedClassLoader isolatedClassLoader,
                                         @NotNull List<Dependency> dependencies,
                                         @NotNull List<Relocation> relocations) {
        super.loadDependencies(isolatedClassLoader, dependencies, relocations);
    }

    /**
     * Gets the plugin's class loader helper.
     *
     * @return the URLClassLoaderHelper instance
     */
    @NotNull
    public URLClassLoaderHelper getClassLoaderHelper() {
        return classLoaderHelper;
    }
}
