package org.bxteam.quark.classloader;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of {@link IsolatedClassLoader} using {@link URLClassLoader}.
 *
 * <p>This implementation extends URLClassLoader and uses the system class loader's
 * parent (typically the platform class loader) as its parent to provide isolation
 * from application classes while maintaining access to core Java classes.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Parallel class loading support</li>
 *   <li>Isolated from application classpath</li>
 *   <li>Thread-safe path addition</li>
 *   <li>Proper resource cleanup</li>
 * </ul>
 */
public class IsolatedClassLoaderImpl extends URLClassLoader implements IsolatedClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger pathCount = new AtomicInteger(0);

    /**
     * Creates a new isolated class loader with no initial URLs.
     */
    public IsolatedClassLoaderImpl() {
        this(new URL[0]);
    }

    /**
     * Creates a new isolated class loader with the specified URLs.
     *
     * @param urls the initial URLs to add to the classpath
     * @throws NullPointerException if urls is null
     */
    public IsolatedClassLoaderImpl(@NotNull URL... urls) {
        super(requireNonNull(urls, "URLs cannot be null"), getIsolatedParent());
        pathCount.set(urls.length);
    }

    /**
     * Creates a new isolated class loader with a custom parent.
     *
     * @param parent the parent class loader
     * @param urls the initial URLs to add to the classpath
     * @throws NullPointerException if parent or urls is null
     */
    public IsolatedClassLoaderImpl(@NotNull ClassLoader parent, @NotNull URL... urls) {
        super(requireNonNull(urls, "URLs cannot be null"), requireNonNull(parent, "Parent cannot be null"));
        pathCount.set(urls.length);
    }

    /**
     * Gets the appropriate parent class loader for isolation.
     * Uses the system class loader's parent to avoid application classpath pollution.
     */
    @NotNull
    private static ClassLoader getIsolatedParent() {
        ClassLoader systemParent = ClassLoader.getSystemClassLoader().getParent();
        return systemParent != null ? systemParent : ClassLoader.getSystemClassLoader();
    }

    @Override
    public synchronized void addURL(@NotNull URL url) {
        checkNotClosed();
        super.addURL(requireNonNull(url, "URL cannot be null"));
        pathCount.incrementAndGet();
    }

    @Override
    public void addPath(@NotNull Path path) {
        requireNonNull(path, "Path cannot be null");
        checkNotClosed();

        try {
            addURL(path.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new ClassLoaderException("Invalid path for URL conversion: " + path, e);
        }
    }

    @Override
    @NotNull
    public Class<?> loadClass(@NotNull String className) throws ClassNotFoundException {
        requireNonNull(className, "Class name cannot be null");
        checkNotClosed();

        return super.loadClass(className);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                super.close();
            } catch (IOException e) {
                throw new ClassLoaderException("Failed to close class loader", e);
            }
        }
    }

    @Override
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public int getPathCount() {
        return pathCount.get();
    }

    /**
     * Gets all URLs currently in this class loader.
     *
     * @return array of URLs
     */
    @NotNull
    public URL[] getAllURLs() {
        checkNotClosed();
        return getURLs();
    }

    /**
     * Checks if this class loader has been closed and throws an exception if it has.
     */
    private void checkNotClosed() {
        if (closed.get()) {
            throw new ClassLoaderException("Class loader has been closed");
        }
    }

    @Override
    public String toString() {
        return "IsolatedClassLoaderImpl{" +
                "pathCount=" + pathCount.get() +
                ", closed=" + closed.get() +
                ", parent=" + getParent().getClass().getSimpleName() +
                '}';
    }
}
