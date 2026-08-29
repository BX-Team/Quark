package org.bxteam.quark.classloader;

import org.bxteam.quark.LibraryManager;
import org.jetbrains.annotations.NotNull;

/**
 * Service provider interface used by {@link ClassLoaderAppender#detect(Object, LibraryManager)}.
 *
 * <p>Platform adapters register implementations in
 * {@code META-INF/services/org.bxteam.quark.classloader.ClassLoaderAppenderProvider}.
 * Implementations must have a public no-argument constructor and must not touch server classes before
 * {@link #supports(Object)} returned true.</p>
 */
public interface ClassLoaderAppenderProvider {
    /**
     * @param plugin the plugin instance
     * @return true if this provider can create an appender for the plugin on the running server
     */
    boolean supports(@NotNull Object plugin);

    /**
     * Creates the appender. Only called after {@link #supports(Object)} returned true.
     *
     * @param plugin the plugin instance
     * @param libraryManager the library manager the appender is created for
     * @return the appender
     */
    @NotNull
    ClassLoaderAppender create(@NotNull Object plugin, @NotNull LibraryManager libraryManager);

    /**
     * Priority among supporting providers, higher wins.
     *
     * @return the priority
     */
    default int priority() {
        return 0;
    }
}
