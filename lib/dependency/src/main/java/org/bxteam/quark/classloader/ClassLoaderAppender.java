package org.bxteam.quark.classloader;

import org.bxteam.quark.LibraryManager;
import org.jetbrains.annotations.NotNull;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import static java.util.Objects.requireNonNull;

/**
 * Adds JAR files to the class path of a plugin.
 *
 * <p>Platform adapters ({@code quark-paper}, {@code quark-bukkit}, {@code quark-velocity}) provide
 * implementations through {@link ClassLoaderAppenderProvider}, found by {@link #detect(Object, LibraryManager)}.
 * Without an adapter, pass an appender to {@link LibraryManager.Builder#appender(ClassLoaderAppender)} directly,
 * for example {@link #of(URLClassLoader, LibraryManager)}.</p>
 */
@FunctionalInterface
public interface ClassLoaderAppender {
    /**
     * Adds the JAR to the class path.
     *
     * @param jarPath the JAR file
     * @throws RuntimeException if the JAR cannot be added
     */
    void append(@NotNull Path jarPath);

    /**
     * Creates an appender for a {@link URLClassLoader}, opening {@code URLClassLoader#addURL} by reflection,
     * {@code Unsafe} or a Java agent when needed.
     *
     * @param classLoader the class loader to add JARs to
     * @param libraryManager the library manager, used to download the Java agent if that fallback is needed
     * @return the appender
     */
    @NotNull
    static ClassLoaderAppender of(@NotNull URLClassLoader classLoader, @NotNull LibraryManager libraryManager) {
        URLClassLoaderHelper helper = new URLClassLoaderHelper(classLoader, libraryManager);
        return helper::addToClasspath;
    }

    /**
     * Finds the appender for the given plugin through the {@link ClassLoaderAppenderProvider}s registered
     * with {@link ServiceLoader}. The supporting provider with the highest priority wins.
     *
     * @param plugin the plugin instance
     * @param libraryManager the library manager the appender is created for
     * @return the appender
     * @throws IllegalStateException if no provider supports the plugin
     */
    @NotNull
    static ClassLoaderAppender detect(@NotNull Object plugin, @NotNull LibraryManager libraryManager) {
        requireNonNull(plugin, "Plugin cannot be null");
        requireNonNull(libraryManager, "Library manager cannot be null");

        ClassLoaderAppenderProvider selected = null;
        List<String> failures = new ArrayList<>();
        Iterator<ClassLoaderAppenderProvider> providers = ServiceLoader
                .load(ClassLoaderAppenderProvider.class, ClassLoaderAppender.class.getClassLoader())
                .iterator();

        while (true) {
            ClassLoaderAppenderProvider provider;
            try {
                if (!providers.hasNext()) break;
                provider = providers.next();
            } catch (ServiceConfigurationError | LinkageError e) {
                failures.add(e.toString());
                continue;
            }

            try {
                if (provider.supports(plugin) && (selected == null || provider.priority() > selected.priority())) {
                    selected = provider;
                }
            } catch (LinkageError e) {
                failures.add(provider.getClass().getName() + ": " + e);
            }
        }

        if (selected == null) {
            throw new IllegalStateException("No ClassLoaderAppender supports " + plugin.getClass().getName()
                    + ". Add the platform adapter (quark-paper, quark-bukkit, quark-velocity, ...) or pass an appender explicitly"
                    + (failures.isEmpty() ? "" : "; skipped providers: " + failures));
        }

        return selected.create(plugin, libraryManager);
    }
}
