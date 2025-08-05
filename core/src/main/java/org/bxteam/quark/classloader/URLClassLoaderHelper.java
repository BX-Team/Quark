package org.bxteam.quark.classloader;

import org.bxteam.quark.LibraryManager;
import org.jetbrains.annotations.NotNull;

import java.net.URLClassLoader;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Helper class for adding JARs to URLClassLoader instances.
 *
 * <p>This class provides a convenient wrapper around URLClassLoaderAccessor
 * for use in library managers. It handles the creation and management of
 * the appropriate accessor for the given URLClassLoader.</p>
 */
public class URLClassLoaderHelper {
    private final URLClassLoaderAccessor accessor;
    private final LibraryManager libraryManager;

    /**
     * Creates a new URLClassLoader helper.
     *
     * @param classLoader the URLClassLoader to manage
     * @param libraryManager the library manager instance
     * @throws NullPointerException if any parameter is null
     * @throws IllegalStateException if an accessor cannot be created
     */
    public URLClassLoaderHelper(@NotNull URLClassLoader classLoader, @NotNull LibraryManager libraryManager) {
        this.libraryManager = requireNonNull(libraryManager, "Library manager cannot be null");
        this.accessor = URLClassLoaderAccessor.create(requireNonNull(classLoader, "Class loader cannot be null"));

        if (accessor == null) {
            throw new IllegalStateException("Failed to create URLClassLoader accessor for: " + classLoader.getClass());
        }
    }

    /**
     * Adds a JAR file to the URLClassLoader's classpath.
     *
     * @param jarPath the path to the JAR file
     * @throws URLClassLoaderAccessor.ClassLoaderAccessException if the JAR cannot be added
     * @throws NullPointerException if jarPath is null
     */
    public void addToClasspath(@NotNull Path jarPath) {
        requireNonNull(jarPath, "JAR path cannot be null");

        try {
            accessor.addJarToClasspath(jarPath);
            libraryManager.getLogger().debug("Added JAR to classpath: " + jarPath.getFileName());
        } catch (Exception e) {
            throw new URLClassLoaderAccessor.ClassLoaderAccessException("Failed to add JAR to classpath: " + jarPath, e);
        }
    }

    /**
     * Gets the underlying URLClassLoader accessor.
     *
     * @return the accessor instance
     */
    @NotNull
    public URLClassLoaderAccessor getAccessor() {
        return accessor;
    }

    /**
     * Gets the accessor type name.
     *
     * @return the accessor type identifier
     */
    @NotNull
    public String getAccessorType() {
        return accessor.getType();
    }

    @Override
    public String toString() {
        return "URLClassLoaderHelper{" +
                "accessorType=" + getAccessorType() +
                '}';
    }
}
