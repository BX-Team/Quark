package org.bxteam.quark.bukkit;

import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.classloader.ClassLoaderAppender;
import org.bxteam.quark.classloader.ClassLoaderAppenderProvider;
import org.jetbrains.annotations.NotNull;

import java.net.URLClassLoader;

/**
 * Adds JARs to the class loader of a Bukkit plugin. Only used when {@code quark-dependency} is present.
 */
public class BukkitClassLoaderAppenderProvider implements ClassLoaderAppenderProvider {
    @Override
    public boolean supports(@NotNull Object plugin) {
        return BukkitClasses.isBukkitPlugin(plugin) && plugin.getClass().getClassLoader() instanceof URLClassLoader;
    }

    @Override
    @NotNull
    public ClassLoaderAppender create(@NotNull Object plugin, @NotNull LibraryManager libraryManager) {
        return ClassLoaderAppender.of((URLClassLoader) plugin.getClass().getClassLoader(), libraryManager);
    }
}
