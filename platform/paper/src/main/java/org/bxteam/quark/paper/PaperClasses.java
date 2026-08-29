package org.bxteam.quark.paper;

import org.jetbrains.annotations.NotNull;

/**
 * Class presence checks that are safe to run when the Paper API is missing.
 */
final class PaperClasses {
    private static final String PLUGIN_CLASS = "org.bukkit.plugin.Plugin";
    private static final String PAPER_MARKER_CLASS = "com.destroystokyo.paper.event.server.ServerTickStartEvent";
    static final String PAPER_PLUGIN_CLASS_LOADER = "io.papermc.paper.plugin.entrypoint.classloader.PaperPluginClassLoader";

    private PaperClasses() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    static boolean isPaperPlugin(@NotNull Object plugin) {
        ClassLoader classLoader = plugin.getClass().getClassLoader();
        try {
            Class.forName(PAPER_MARKER_CLASS, false, classLoader);
            return Class.forName(PLUGIN_CLASS, false, classLoader).isInstance(plugin);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
