package org.bxteam.quark.bungee;

import org.jetbrains.annotations.NotNull;

/**
 * Class presence checks that are safe to run when the BungeeCord API is missing.
 */
final class BungeeClasses {
    private static final String PLUGIN_CLASS = "net.md_5.bungee.api.plugin.Plugin";

    private BungeeClasses() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    static boolean isBungeePlugin(@NotNull Object plugin) {
        try {
            return Class.forName(PLUGIN_CLASS, false, plugin.getClass().getClassLoader()).isInstance(plugin);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
