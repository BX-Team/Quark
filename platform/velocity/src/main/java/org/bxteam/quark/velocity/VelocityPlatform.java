package org.bxteam.quark.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.ProxyVersion;
import org.bxteam.quark.common.QuarkLogger;
import org.bxteam.quark.common.SemanticVersion;
import org.bxteam.quark.platform.Platform;
import org.bxteam.quark.platform.PlatformType;
import org.bxteam.quark.platform.PlatformVersion;
import org.bxteam.quark.platform.Scheduler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * {@link Platform} for Velocity proxies.
 *
 * <p>Velocity has no static access to the proxy, so this platform cannot be created by
 * {@link Platform#detect(Object)}; use {@link #create(ProxyServer, Object, Path)} with the injected values.</p>
 */
public class VelocityPlatform implements Platform {
    private final Path dataDirectory;
    private final QuarkLogger logger;
    private final VelocityScheduler scheduler;
    private final PlatformVersion version;

    /**
     * @param server the proxy server
     * @param plugin the plugin instance
     * @param dataDirectory the plugin data directory
     * @param logger the plugin logger
     */
    protected VelocityPlatform(@NotNull ProxyServer server, @NotNull Object plugin,
                               @NotNull Path dataDirectory, @NotNull Logger logger) {
        requireNonNull(server, "Server cannot be null");
        requireNonNull(plugin, "Plugin cannot be null");
        this.dataDirectory = requireNonNull(dataDirectory, "Data directory cannot be null");
        this.logger = new Slf4jQuarkLogger(requireNonNull(logger, "Logger cannot be null"));
        this.scheduler = new VelocityScheduler(server, plugin);
        this.version = detectVersion(server.getVersion());
    }

    /**
     * Creates the platform for a plugin, logging through the plugin's logger.
     *
     * @param server the proxy server, usually {@code @Inject}ed
     * @param plugin the plugin instance
     * @param dataDirectory the plugin data directory, usually {@code @Inject @DataDirectory}
     * @return the platform
     * @throws IllegalArgumentException if the object is not a registered plugin
     */
    @NotNull
    public static VelocityPlatform create(@NotNull ProxyServer server, @NotNull Object plugin, @NotNull Path dataDirectory) {
        requireNonNull(server, "Server cannot be null");
        requireNonNull(plugin, "Plugin cannot be null");

        PluginContainer container = server.getPluginManager().fromInstance(plugin)
                .orElseThrow(() -> new IllegalArgumentException(plugin.getClass().getName() + " is not a registered Velocity plugin"));
        return new VelocityPlatform(server, plugin, dataDirectory, LoggerFactory.getLogger(container.getDescription().getId()));
    }

    /**
     * Creates the platform for a plugin.
     *
     * @param server the proxy server, usually {@code @Inject}ed
     * @param plugin the plugin instance
     * @param dataDirectory the plugin data directory, usually {@code @Inject @DataDirectory}
     * @param logger the plugin logger, usually {@code @Inject}ed
     * @return the platform
     */
    @NotNull
    public static VelocityPlatform create(@NotNull ProxyServer server, @NotNull Object plugin,
                                          @NotNull Path dataDirectory, @NotNull Logger logger) {
        return new VelocityPlatform(server, plugin, dataDirectory, logger);
    }

    private static PlatformVersion detectVersion(ProxyVersion proxyVersion) {
        // e.g. "3.3.0-SNAPSHOT (git-1a2b3c4d-b400)"
        String raw = proxyVersion.getVersion();
        int space = raw.indexOf(' ');
        return new PlatformVersion(proxyVersion.getName(), SemanticVersion.parse(space > 0 ? raw.substring(0, space) : raw), null);
    }

    @Override
    @NotNull
    public String id() {
        return "velocity";
    }

    @Override
    @NotNull
    public PlatformType type() {
        return PlatformType.VELOCITY;
    }

    @Override
    @NotNull
    public Path dataDirectory() {
        return dataDirectory;
    }

    @Override
    @NotNull
    public QuarkLogger logger() {
        return logger;
    }

    @Override
    @NotNull
    public Scheduler scheduler() {
        return scheduler;
    }

    @Override
    @NotNull
    public PlatformVersion version() {
        return version;
    }
}
