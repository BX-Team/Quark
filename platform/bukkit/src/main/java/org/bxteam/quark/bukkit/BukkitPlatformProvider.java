package org.bxteam.quark.bukkit;

import org.bukkit.plugin.Plugin;
import org.bxteam.quark.platform.Platform;
import org.bxteam.quark.platform.PlatformProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Registers {@link BukkitPlatform} for {@link Platform#detect(Object)}.
 */
public class BukkitPlatformProvider implements PlatformProvider {
    @Override
    public boolean supports(@NotNull Object plugin) {
        return BukkitClasses.isBukkitPlugin(plugin);
    }

    @Override
    @NotNull
    public Platform create(@NotNull Object plugin) {
        return BukkitPlatform.create((Plugin) plugin);
    }
}
