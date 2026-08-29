package org.bxteam.quark.paper;

import org.bukkit.plugin.Plugin;
import org.bxteam.quark.platform.Platform;
import org.bxteam.quark.platform.PlatformProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Registers {@link PaperPlatform} for {@link Platform#detect(Object)}. Takes precedence over the Bukkit adapter.
 */
public class PaperPlatformProvider implements PlatformProvider {
    @Override
    public boolean supports(@NotNull Object plugin) {
        return PaperClasses.isPaperPlugin(plugin);
    }

    @Override
    @NotNull
    public Platform create(@NotNull Object plugin) {
        return PaperPlatform.create((Plugin) plugin);
    }

    @Override
    public int priority() {
        return 10;
    }
}
