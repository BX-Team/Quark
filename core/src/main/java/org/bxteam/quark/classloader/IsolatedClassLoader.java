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
 * <p>Implementations must provide dynamic JAR addition to classpath, class loading
 * from added JARs, proper resource cleanup, and isolation from parent classloaders
 * where appropriate.</p>
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
     * @return true if the class loader is closed, false otherwise
     */
    default boolean isClosed() {
        return false;
    }

    /**
     * Gets the number of paths added to this class loader.
     *
     * @return the number of paths, or -1 if not supported by the implementation
     */
    default int getPathCount() {
        return -1;
    }

    /**
     * Exception thrown when class loader operations fail.
     *
     * @since 1.0
     */
    class ClassLoaderException extends RuntimeException {
        /**
         * Constructs a new ClassLoaderException with the specified detail message.
         *
         * @param message the detail message
         */
        public ClassLoaderException(String message) {
            super(message);
        }

        /**
         * Constructs a new ClassLoaderException with the specified detail message and cause.
         *
         * @param message the detail message
         * @param cause the cause of this exception
         */
        public ClassLoaderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
