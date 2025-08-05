package org.bxteam.quark.velocity;

import com.velocitypowered.api.plugin.PluginManager;
import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.bxteam.quark.classloader.IsolatedClassLoaderImpl;
import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.relocation.Relocation;
import org.bxteam.quark.velocity.logger.adapters.VelocityLogAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Velocity-specific implementation of LibraryManager for Velocity plugins.
 *
 * <p>This implementation uses Velocity's PluginManager to add JARs to the plugin's
 * classpath at runtime. It supports both Velocity 3.x and later versions.</p>
 *
 * @param <T> the plugin type (typically your main plugin class)
 */
public class VelocityLibraryManager<T> extends LibraryManager {
    private final PluginManager pluginManager;
    private final T plugin;

    /**
     * Creates a new Velocity library manager with default settings.
     *
     * @param plugin the Velocity plugin instance
     * @param logger the SLF4J logger from Velocity
     * @param dataDirectory the plugin's data directory
     * @param pluginManager the Velocity plugin manager
     * @throws NullPointerException if any parameter is null
     */
    public VelocityLibraryManager(@NotNull T plugin,
                                  @NotNull Logger logger,
                                  @NotNull Path dataDirectory,
                                  @NotNull PluginManager pluginManager) {
        this(plugin, logger, dataDirectory, pluginManager, "libs");
    }

    /**
     * Creates a new Velocity library manager with custom directory name.
     *
     * @param plugin the Velocity plugin instance
     * @param logger the SLF4J logger from Velocity
     * @param dataDirectory the plugin's data directory
     * @param pluginManager the Velocity plugin manager
     * @param librariesDirectoryName the name of the directory to store downloaded libraries
     * @throws NullPointerException if any parameter is null
     */
    public VelocityLibraryManager(@NotNull T plugin,
                                  @NotNull Logger logger,
                                  @NotNull Path dataDirectory,
                                  @NotNull PluginManager pluginManager,
                                  @NotNull String librariesDirectoryName) {
        this(plugin, new VelocityLogAdapter(logger), dataDirectory, pluginManager, librariesDirectoryName);
    }

    /**
     * Creates a new Velocity library manager with custom settings.
     *
     * @param plugin the Velocity plugin instance
     * @param logAdapter the log adapter to use for logging
     * @param dataDirectory the plugin's data directory
     * @param pluginManager the Velocity plugin manager
     * @param librariesDirectoryName the name of the directory to store downloaded libraries
     * @throws NullPointerException if any parameter is null
     */
    public VelocityLibraryManager(@NotNull T plugin,
                                  @NotNull LogAdapter logAdapter,
                                  @NotNull Path dataDirectory,
                                  @NotNull PluginManager pluginManager,
                                  @NotNull String librariesDirectoryName) {
        super(logAdapter, dataDirectory, librariesDirectoryName);

        this.plugin = requireNonNull(plugin, "Plugin cannot be null");
        this.pluginManager = requireNonNull(pluginManager, "Plugin manager cannot be null");

        logger.debug("Initialized VelocityLibraryManager for plugin: " + plugin.getClass().getSimpleName());
    }

    @Override
    protected void addToClasspath(@NotNull Path jarPath) {
        requireNonNull(jarPath, "JAR path cannot be null");

        try {
            pluginManager.addToClasspath(plugin, jarPath);
            logger.debug("Added to Velocity classpath: " + jarPath);
        } catch (Exception e) {
            throw new LibraryLoadException("Failed to add JAR to Velocity plugin classpath: " + jarPath, e);
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

        ClassLoader pluginClassLoader = plugin.getClass().getClassLoader();
        InputStream stream = pluginClassLoader.getResourceAsStream(resourcePath);

        if (stream == null) {
            stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        }

        return stream;
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

        Dependency dependency = Dependency.of(groupId, artifactId, version);
        loadDependency(dependency);
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
}
