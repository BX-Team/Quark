package org.bxteam.quark.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bxteam.quark.common.JulLogger;
import org.bxteam.quark.common.QuarkLogger;
import org.bxteam.quark.common.SemanticVersion;
import org.bxteam.quark.platform.Platform;
import org.bxteam.quark.platform.PlatformException;
import org.bxteam.quark.platform.PlatformType;
import org.bxteam.quark.platform.PlatformVersion;
import org.bxteam.quark.platform.Scheduler;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * {@link Platform} for Bukkit and Spigot servers.
 */
public class BukkitPlatform implements Platform {
    private final Plugin plugin;
    private final QuarkLogger logger;
    private final Scheduler scheduler;
    private final PlatformVersion version;

    /**
     * @param plugin the plugin
     * @param scheduler the scheduler bound to the plugin
     */
    protected BukkitPlatform(@NotNull Plugin plugin, @NotNull Scheduler scheduler) {
        this.plugin = requireNonNull(plugin, "Plugin cannot be null");
        this.scheduler = requireNonNull(scheduler, "Scheduler cannot be null");
        this.logger = new JulLogger(plugin.getLogger());
        this.version = detectVersion();
    }

    /**
     * Creates the platform for a plugin.
     *
     * @param plugin the plugin
     * @return the platform
     * @throws PlatformException on Folia, which needs the {@code quark-paper} adapter
     */
    @NotNull
    public static BukkitPlatform create(@NotNull Plugin plugin) {
        requireNonNull(plugin, "Plugin cannot be null");
        if (isFolia()) {
            throw new PlatformException("Folia does not support the Bukkit scheduler, use the quark-paper adapter");
        }
        return new BukkitPlatform(plugin, new BukkitTaskScheduler(plugin));
    }

    /**
     * @return true if the server is Folia
     */
    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static PlatformVersion detectVersion() {
        String bukkitVersion = Bukkit.getBukkitVersion(); // looks like "1.20.4-R0.1-SNAPSHOT"
        int separator = bukkitVersion.indexOf('-');
        String minecraft = separator > 0 ? bukkitVersion.substring(0, separator) : bukkitVersion;

        return new PlatformVersion(Bukkit.getName(), SemanticVersion.parse(Bukkit.getVersion()), SemanticVersion.parse(minecraft));
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
        return "bukkit";
    }

    @Override
    @NotNull
    public PlatformType type() {
        return PlatformType.BUKKIT;
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
