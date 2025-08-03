package org.bxteam.quark.classloader;

import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.nio.file.Path;

/**
 * Interface for isolated class loaders that can dynamically load JAR files.
 *
 * <p>Isolated class loaders provide a clean environment for loading dependencies
 * without polluting the main application classpath. This is essential for
 * avoiding conflicts between different versions of the same library.</p>
 *
 * <p>Implementations should provide:</p>
 * <ul>
 *   <li>Dynamic JAR addition to classpath</li>
 *   <li>Class loading from added JARs</li>
 *   <li>Proper resource cleanup</li>
 *   <li>Isolation from parent classloaders where appropriate</li>
 * </ul>
 */
public interface IsolatedClassLoader extends Closeable {
    /**
     * Adds a JAR file or directory to this class loader's classpath.
     *
     * @param path the path to the JAR file or directory to add
     * @throws NullPointerException if path is null
     * @throws ClassLoaderException if the path cannot be added
     */
    void addPath(@NotNull Path path);

    /**
     * Loads a class by name from this class loader.
     *
     * @param className the fully qualified name of the class to load
     * @return the loaded class
     * @throws ClassNotFoundException if the class cannot be found
     * @throws NullPointerException if className is null
     */
    @NotNull
    Class<?> loadClass(@NotNull String className) throws ClassNotFoundException;

    /**
     * Closes this class loader and releases any associated resources.
     *
     * <p>After calling this method, the class loader should not be used
     * for loading classes or adding paths.</p>
     *
     * @throws ClassLoaderException if cleanup fails
     */
    @Override
    void close();

    /**
     * Checks if this class loader has been closed.
     *
     * @return true if the class loader is closed
     */
    default boolean isClosed() {
        return false;
    }

    /**
     * Gets the number of paths added to this class loader.
     *
     * @return the number of paths, or -1 if not supported
     */
    default int getPathCount() {
        return -1;
    }

    /**
     * Exception thrown when class loader operations fail.
     */
    class ClassLoaderException extends RuntimeException {
        public ClassLoaderException(String message) {
            super(message);
        }

        public ClassLoaderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
