package org.bxteam.quark.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import org.bxteam.quark.platform.Platform;
import org.bxteam.quark.platform.PlatformProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Registers {@link BungeePlatform} for {@link Platform#detect(Object)}.
 */
public class BungeePlatformProvider implements PlatformProvider {
    @Override
    public boolean supports(@NotNull Object plugin) {
        return BungeeClasses.isBungeePlugin(plugin);
    }

    @Override
    @NotNull
    public Platform create(@NotNull Object plugin) {
        return BungeePlatform.create((Plugin) plugin);
    }
}
