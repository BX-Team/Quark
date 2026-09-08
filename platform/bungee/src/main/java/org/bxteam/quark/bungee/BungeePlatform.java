package org.bxteam.quark.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import org.bxteam.quark.common.JulLogger;
import org.bxteam.quark.common.QuarkLogger;
import org.bxteam.quark.common.SemanticVersion;
import org.bxteam.quark.platform.Platform;
import org.bxteam.quark.platform.PlatformType;
import org.bxteam.quark.platform.PlatformVersion;
import org.bxteam.quark.platform.Scheduler;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * {@link Platform} for BungeeCord and Waterfall proxies.
 */
public class BungeePlatform implements Platform {
    private final Plugin plugin;
    private final QuarkLogger logger;
    private final Scheduler scheduler;
    private final PlatformVersion version;

    /**
     * @param plugin the plugin
     */
    protected BungeePlatform(@NotNull Plugin plugin) {
        this.plugin = requireNonNull(plugin, "Plugin cannot be null");
        this.logger = new JulLogger(plugin.getLogger());
        this.scheduler = new BungeeTaskScheduler(plugin);
        this.version = detectVersion(plugin.getProxy());
    }

    /**
     * Creates the platform for a plugin.
     *
     * @param plugin the plugin
     * @return the platform
     */
    @NotNull
    public static BungeePlatform create(@NotNull Plugin plugin) {
        return new BungeePlatform(plugin);
    }

    private static PlatformVersion detectVersion(ProxyServer proxy) {
        return new PlatformVersion(proxy.getName(), SemanticVersion.parse(proxy.getVersion()), null); // version looks like "git:BungeeCord-Bootstrap:1.20-R0.1-SNAPSHOT:abc1234:1800"
    }

    /**
     * @return the plugin this platform is bound to
     */
    @NotNull
    public Plugin plugin() {
        return plugin;
    }

    @Override
    @NotNull
    public String id() {
        return "bungee";
    }

    @Override
    @NotNull
    public PlatformType type() {
        return PlatformType.BUNGEE;
    }

    @Override
    @NotNull
    public Path dataDirectory() {
        return plugin.getDataFolder().toPath();
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
