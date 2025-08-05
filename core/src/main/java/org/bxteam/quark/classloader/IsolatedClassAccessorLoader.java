package org.bxteam.quark.classloader;

import org.jetbrains.annotations.NotNull;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * Isolated class loader implementation that uses a {@link URLClassLoaderAccessor}
 * to add JARs to an existing URLClassLoader.
 *
 * <p>This implementation is useful when you need to add JARs to an existing
 * class loader (such as a plugin's class loader) rather than creating a new
 * isolated environment. It uses reflection-based or unsafe-based techniques
 * to modify the existing class loader's classpath.</p>
 */
public class IsolatedClassAccessorLoader implements IsolatedClassLoader {
    private final URLClassLoaderAccessor accessor;
    private final URLClassLoader classLoader;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a new accessor-based isolated class loader.
     *
     * @param classLoader the URLClassLoader to modify
     * @throws NullPointerException if classLoader is null
     * @throws ClassLoaderException if the accessor cannot be created
     */
    public IsolatedClassAccessorLoader(@NotNull URLClassLoader classLoader) {
        this.classLoader = requireNonNull(classLoader, "Class loader cannot be null");
        this.accessor = URLClassLoaderAccessor.create(classLoader);

        if (this.accessor == null) {
            throw new ClassLoaderException("Failed to create URLClassLoader accessor");
        }
    }

    @Override
    public void addPath(@NotNull Path path) {
        requireNonNull(path, "Path cannot be null");
        checkNotClosed();

        try {
            accessor.addJarToClasspath(path);
        } catch (Exception e) {
            throw new ClassLoaderException("Failed to add path to classpath: " + path, e);
        }
    }

    @Override
    @NotNull
    public Class<?> loadClass(@NotNull String className) throws ClassNotFoundException {
        requireNonNull(className, "Class name cannot be null");
        checkNotClosed();

        return classLoader.loadClass(className);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                classLoader.close();
            } catch (Exception e) {
                throw new ClassLoaderException("Failed to close underlying class loader", e);
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public int getPathCount() {
        try {
            return classLoader.getURLs().length;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Gets the underlying URLClassLoader.
     *
     * @return the wrapped class loader
     */
    @NotNull
    public URLClassLoader getUnderlyingClassLoader() {
        return classLoader;
    }

    /**
     * Gets the accessor used for modifying the class loader.
     *
     * @return the URLClassLoader accessor
     */
    @NotNull
    public URLClassLoaderAccessor getAccessor() {
        return accessor;
    }

    /**
     * Checks if this class loader has been closed and throws an exception if it has.
     *
     * @throws ClassLoaderException if the class loader has been closed
     */
    private void checkNotClosed() {
        if (closed.get()) {
            throw new ClassLoaderException("Class loader has been closed");
        }
    }

    @Override
    public String toString() {
        return "IsolatedClassAccessorLoader{" +
                "classLoader=" + classLoader.getClass().getSimpleName() +
                ", pathCount=" + getPathCount() +
                ", closed=" + closed.get() +
                ", accessorType=" + accessor.getClass().getSimpleName() +
                '}';
    }
}
