package org.bxteam.quark.sponge.classloader;

import org.bxteam.quark.classloader.URLClassLoaderHelper;
import org.bxteam.quark.sponge.SpongeLibraryManager;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Helper class for adding JARs to Sponge plugin class loaders.
 *
 * <p>This class accesses Sponge's internal URLClassLoader through reflection
 * to add dependencies to the plugin's classpath at runtime.</p>
 */
public class SpongeClassLoaderHelper {
    /**
     * The URLClassLoader helper of Quark
     */
    @NotNull
    private final URLClassLoaderHelper classLoader;

    /**
     * Creates a new Sponge class loader helper that wraps a {@link URLClassLoaderHelper}.
     *
     * @param pluginClassLoader the class loader of the plugin
     * @param libraryManager the library manager used to download dependencies
     * @throws NullPointerException if any parameter is null
     * @throws RuntimeException if the delegated class loader cannot be accessed
     */
    public SpongeClassLoaderHelper(@NotNull ClassLoader pluginClassLoader, @NotNull SpongeLibraryManager<?> libraryManager) {
        requireNonNull(pluginClassLoader, "pluginClassLoader");
        requireNonNull(libraryManager, "libraryManager");

        try {
            Field delegatedClassLoaderField = pluginClassLoader.getClass().getDeclaredField("delegatedClassLoader");
            delegatedClassLoaderField.setAccessible(true);

            URLClassLoader spongeClassLoader = (URLClassLoader) delegatedClassLoaderField.get(pluginClassLoader);
            classLoader = new URLClassLoaderHelper(spongeClassLoader, libraryManager);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access Sponge's delegated class loader", e);
        }
    }

    /**
     * Adds a path to the class loader's classpath.
     *
     * @param path the path to add
     * @throws NullPointerException if path is null
     */
    public void addToClasspath(@NotNull Path path) {
        requireNonNull(path, "path");
        classLoader.addToClasspath(path);
    }
}
