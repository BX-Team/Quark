package org.bxteam.quark.platform;

import org.jetbrains.annotations.NotNull;

/**
 * Service provider interface used by {@link Platform#detect(Object)}.
 *
 * <p>Adapters register implementations in {@code META-INF/services/org.bxteam.quark.platform.PlatformProvider}.
 * Implementations must have a public no-argument constructor and must not touch server classes before
 * {@link #supports(Object)} returned true.</p>
 */
public interface PlatformProvider {
    /**
     * Checks whether this adapter handles the given plugin on the running server.
     *
     * @param plugin the plugin instance
     * @return true if {@link #create(Object)} can be called for this plugin
     */
    boolean supports(@NotNull Object plugin);

    /**
     * Creates the platform for the plugin. Only called after {@link #supports(Object)} returned true.
     *
     * @param plugin the plugin instance
     * @return the platform
     * @throws PlatformException if the platform cannot be created from the plugin instance
     */
    @NotNull
    Platform create(@NotNull Object plugin);

    /**
     * Priority among supporting providers, higher wins. Used when several adapters match the same server,
     * e.g. Paper and Bukkit.
     *
     * @return the priority
     */
    default int priority() {
        return 0;
    }
}
