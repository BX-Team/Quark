package org.bxteam.quark.bukkit;

import org.jetbrains.annotations.NotNull;

/**
 * Class presence checks that are safe to run when the Bukkit API is missing.
 */
final class BukkitClasses {
    private static final String PLUGIN_CLASS = "org.bukkit.plugin.Plugin";

    private BukkitClasses() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    static boolean isBukkitPlugin(@NotNull Object plugin) {
        try {
            return Class.forName(PLUGIN_CLASS, false, plugin.getClass().getClassLoader()).isInstance(plugin);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
