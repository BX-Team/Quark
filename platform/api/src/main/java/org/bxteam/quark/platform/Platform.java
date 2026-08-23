package org.bxteam.quark.platform;

import org.bxteam.quark.common.QuarkLogger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import static java.util.Objects.requireNonNull;

/**
 * The server a plugin runs on.
 *
 * <p>Instances are created by platform adapters ({@code quark-paper}, {@code quark-bukkit},
 * {@code quark-velocity}, ...), either through their own factories or through {@link #detect(Object)}.
 * Library modules ({@code quark-config}, {@code quark-update}, ...) never depend on this interface.</p>
 */
public interface Platform {
    /**
     * @return the adapter id, e.g. {@code "paper"} or {@code "velocity"}
     */
    @NotNull
    String id();

    /**
     * @return the platform type
     */
    @NotNull
    PlatformType type();

    /**
     * @return the plugin's data directory
     */
    @NotNull
    Path dataDirectory();

    /**
     * @return the plugin's logger
     */
    @NotNull
    QuarkLogger logger();

    /**
     * @return the scheduler bound to the plugin
     */
    @NotNull
    Scheduler scheduler();

    /**
     * @return the server and Minecraft version
     */
    @NotNull
    PlatformVersion version();

    /**
     * Finds the platform adapter for the given plugin instance using {@link ServiceLoader}.
     *
     * <p>Every adapter on the classpath registers a {@link PlatformProvider}; the supporting provider with
     * the highest {@link PlatformProvider#priority() priority} wins (Paper beats Bukkit on a Paper server).
     * Platforms that cannot be built from the plugin instance alone, like Velocity, fail with a message
     * naming the factory to use instead.</p>
     *
     * @param plugin the plugin instance ({@code JavaPlugin}, Velocity {@code @Plugin} class, ...)
     * @return the platform
     * @throws PlatformException if no adapter supports the plugin or the adapter cannot create the platform
     */
    @NotNull
    static Platform detect(@NotNull Object plugin) {
        requireNonNull(plugin, "Plugin cannot be null");

        PlatformProvider selected = null;
        List<String> failures = new ArrayList<>();
        Iterator<PlatformProvider> providers = ServiceLoader.load(PlatformProvider.class, Platform.class.getClassLoader()).iterator();

        while (true) {
            PlatformProvider provider;
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
                // the adapter's server API is not on the classpath, so it is not the running platform
                failures.add(provider.getClass().getName() + ": " + e);
            }
        }

        if (selected == null) {
            throw new PlatformException("No Quark platform adapter supports " + plugin.getClass().getName()
                    + ". Add the adapter for your server (quark-paper, quark-bukkit, quark-velocity, ...) to the plugin"
                    + (failures.isEmpty() ? "" : "; skipped providers: " + failures));
        }

        return selected.create(plugin);
    }
}
