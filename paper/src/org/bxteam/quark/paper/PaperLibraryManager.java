package org.bxteam.quark.paper;

import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.bxteam.quark.classloader.IsolatedClassLoaderImpl;
import org.bxteam.quark.classloader.URLClassLoaderHelper;
import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.logger.adapters.JavaLogAdapter;
import org.bxteam.quark.relocation.Relocation;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Paper-specific implementation of LibraryManager for Paper plugins.
 *
 * <p>This implementation is designed for Paper plugins that use paper-plugin.yml
 * and the PaperPluginClassLoader system introduced in Paper 1.19.3+. It uses
 * reflection to access the internal library loader for dependency injection.</p>
 *
 * @apiNote This is for Paper plugins (paper-plugin.yml), not Bukkit plugins
 * running on Paper. For Bukkit plugins, use <b>quark-bukkit</b> module.
 */
public class PaperLibraryManager extends LibraryManager {
    private final Plugin plugin;
    private final URLClassLoaderHelper classLoaderHelper;

    /**
     * Creates a new Paper library manager with default settings.
     *
     * @param plugin the Paper plugin instance
     * @throws NullPointerException if plugin is null
     * @throws RuntimeException if the plugin is not using PaperPluginClassLoader
     */
    public PaperLibraryManager(@NotNull Plugin plugin) {
        this(plugin, "libs");
    }

    /**
     * Creates a new Paper library manager with custom directory name.
     *
     * @param plugin the Paper plugin instance
     * @param librariesDirectoryName the name of the directory to store downloaded libraries
     * @throws NullPointerException if any parameter is null
     * @throws RuntimeException if the plugin is not using PaperPluginClassLoader
     */
    public PaperLibraryManager(@NotNull Plugin plugin, @NotNull String librariesDirectoryName) {
        this(plugin, librariesDirectoryName, new JavaLogAdapter(plugin.getLogger()));
    }

    /**
     * Creates a new Paper library manager with custom settings.
     *
     * @param plugin the Paper plugin instance
     * @param librariesDirectoryName the name of the directory to store downloaded libraries
     * @param logAdapter the log adapter to use for logging
     * @throws NullPointerException if any parameter is null
     * @throws RuntimeException if the plugin is not using PaperPluginClassLoader
     */
    public PaperLibraryManager(@NotNull Plugin plugin, @NotNull String librariesDirectoryName, @NotNull LogAdapter logAdapter) {
        super(logAdapter, plugin.getDataFolder().toPath(), librariesDirectoryName);

        this.plugin = requireNonNull(plugin, "Plugin cannot be null");
        this.classLoaderHelper = createClassLoaderHelper(plugin);

        logger.debug("Initialized PaperLibraryManager for plugin: " + plugin.getName());
    }

    /**
     * Creates and configures the URLClassLoaderHelper for Paper plugin class loading.
     *
     * @param plugin the Paper plugin instance
     * @return the configured URLClassLoaderHelper
     * @throws RuntimeException if PaperPluginClassLoader is not available or accessible
     */
    @NotNull
    private URLClassLoaderHelper createClassLoaderHelper(@NotNull Plugin plugin) {
        ClassLoader pluginClassLoader = plugin.getClass().getClassLoader();

        Class<?> paperPluginClassLoaderClass;
        try {
            paperPluginClassLoaderClass = Class.forName("io.papermc.paper.plugin.entrypoint.classloader.PaperPluginClassLoader");
        } catch (ClassNotFoundException e) {
            logger.error("PaperPluginClassLoader not found. Are you using Paper 1.19.3+ with paper-plugin.yml?");
            throw new RuntimeException("PaperPluginClassLoader not found - requires Paper 1.19.3+", e);
        }

        if (!paperPluginClassLoaderClass.isAssignableFrom(pluginClassLoader.getClass())) {
            logger.error("Plugin class loader is not a PaperPluginClassLoader. Are you using paper-plugin.yml?");
            logger.error("Current class loader: " + pluginClassLoader.getClass().getName());
            logger.error("Expected: PaperPluginClassLoader (Paper plugin) or PluginClassLoader (Bukkit plugin)");
            throw new RuntimeException("Plugin is not using PaperPluginClassLoader - use BukkitLibraryManager for Bukkit plugins");
        }

        URLClassLoader libraryLoader = extractLibraryLoader(paperPluginClassLoaderClass, pluginClassLoader);

        return new URLClassLoaderHelper(libraryLoader, this);
    }

    /**
     * Extracts the library loader from the PaperPluginClassLoader using reflection.
     *
     * @param paperPluginClassLoaderClass the PaperPluginClassLoader class
     * @param pluginClassLoader the plugin's class loader instance
     * @return the extracted URLClassLoader for library loading
     * @throws RuntimeException if extraction fails
     */
    @NotNull
    private URLClassLoader extractLibraryLoader(@NotNull Class<?> paperPluginClassLoaderClass, @NotNull ClassLoader pluginClassLoader) {
        Field libraryLoaderField;

        try {
            libraryLoaderField = paperPluginClassLoaderClass.getDeclaredField("libraryLoader");
        } catch (NoSuchFieldException e) {
            logger.error("Cannot find libraryLoader field in PaperPluginClassLoader.");
            logger.error("This may indicate a Paper version incompatibility. Please report this issue.");
            throw new RuntimeException("libraryLoader field not found in PaperPluginClassLoader", e);
        }

        libraryLoaderField.setAccessible(true);

        URLClassLoader libraryLoader;
        try {
            libraryLoader = (URLClassLoader) libraryLoaderField.get(pluginClassLoader);
        } catch (IllegalAccessException e) {
            logger.error("Cannot access libraryLoader field in PaperPluginClassLoader");
            throw new RuntimeException("Failed to access libraryLoader field", e);
        } catch (ClassCastException e) {
            logger.error("libraryLoader field is not a URLClassLoader");
            throw new RuntimeException("libraryLoader is not a URLClassLoader", e);
        }

        if (libraryLoader == null) {
            logger.error("libraryLoader field is null in PaperPluginClassLoader");
            throw new RuntimeException("libraryLoader is null");
        }

        logger.debug("Successfully obtained Paper plugin library loader: " + libraryLoader.getClass().getName());
        return libraryLoader;
    }

    @Override
    protected void addToClasspath(@NotNull Path jarPath) {
        requireNonNull(jarPath, "JAR path cannot be null");

        try {
            classLoaderHelper.addToClasspath(jarPath);
        } catch (Exception e) {
            throw new LibraryLoadException("Failed to add JAR to Paper plugin classpath: " + jarPath, e);
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
     * Loads dependencies from Gradle plugin generated metadata.
     *
     * <p>This method reads the metadata files generated by the Quark Gradle plugin
     * and loads all configured dependencies with their associated repositories and
     * relocations into the main plugin classpath.</p>
     *
     * @throws org.bxteam.quark.gradle.GradleMetadataLoader.GradleMetadataException if metadata loading fails
     * @throws LibraryLoadException if dependency loading fails
     */
    public void loadFromGradle() {
        super.loadFromGradle();
    }

    /**
     * Loads dependencies from Gradle metadata into an isolated class loader.
     *
     * @param isolatedClassLoader the isolated class loader to load dependencies into
     * @throws NullPointerException if classLoader is null
     * @throws org.bxteam.quark.gradle.GradleMetadataLoader.GradleMetadataException if metadata loading fails
     * @throws LibraryLoadException if dependency loading fails
     */
    public void loadFromGradleIsolated(@NotNull IsolatedClassLoader isolatedClassLoader) {
        super.loadFromGradle(isolatedClassLoader);
    }

    /**
     * Loads dependencies from Gradle metadata into a named isolated class loader.
     *
     * @param loaderId the unique identifier for the class loader
     * @throws NullPointerException if loaderId is null
     * @throws org.bxteam.quark.gradle.GradleMetadataLoader.GradleMetadataException if metadata loading fails
     * @throws LibraryLoadException if dependency loading fails
     */
    public void loadFromGradleIsolated(@NotNull String loaderId) {
        super.loadFromGradleIsolated(loaderId);
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
