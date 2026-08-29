package org.bxteam.quark.paper;

import org.bukkit.plugin.Plugin;
import org.bxteam.quark.bukkit.BukkitPlatform;
import org.bxteam.quark.platform.Platform;
import org.bxteam.quark.platform.PlatformType;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * {@link Platform} for Paper servers and forks, including Folia.
 */
public class PaperPlatform extends BukkitPlatform {
    private final PaperScheduler scheduler;

    /**
     * @param plugin the plugin
     * @param scheduler the scheduler bound to the plugin
     */
    protected PaperPlatform(@NotNull Plugin plugin, @NotNull PaperScheduler scheduler) {
        super(plugin, scheduler);
        this.scheduler = scheduler;
    }

    /**
     * Creates the platform for a plugin. Works for both {@code plugin.yml} and {@code paper-plugin.yml} plugins.
     *
     * @param plugin the plugin
     * @return the platform
     */
    @NotNull
    public static PaperPlatform create(@NotNull Plugin plugin) {
        requireNonNull(plugin, "Plugin cannot be null");
        return new PaperPlatform(plugin, PaperScheduler.create(plugin));
    }

    @Override
    @NotNull
    public String id() {
        return "paper";
    }

    @Override
    @NotNull
    public PlatformType type() {
        return PlatformType.PAPER;
    }

    /**
     * @return the scheduler, with entity and location overloads for Folia
     */
    @Override
    @NotNull
    public PaperScheduler scheduler() {
        return scheduler;
    }
}
