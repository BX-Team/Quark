package org.bxteam.quark.paper;

import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.classloader.ClassLoaderAppender;
import org.bxteam.quark.classloader.ClassLoaderAppenderProvider;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.net.URLClassLoader;

/**
 * Adds JARs to the class path of a Paper plugin. For {@code paper-plugin.yml} plugins the JARs go to the
 * library loader of the {@code PaperPluginClassLoader}, for {@code plugin.yml} plugins to the plugin class loader.
 * Only used when {@code quark-dependency} is present.
 */
public class PaperClassLoaderAppenderProvider implements ClassLoaderAppenderProvider {
    @Override
    public boolean supports(@NotNull Object plugin) {
        return PaperClasses.isPaperPlugin(plugin);
    }

    @Override
    @NotNull
    public ClassLoaderAppender create(@NotNull Object plugin, @NotNull LibraryManager libraryManager) {
        ClassLoader classLoader = plugin.getClass().getClassLoader();

        Class<?> paperClassLoader = paperPluginClassLoaderClass(classLoader);
        if (paperClassLoader != null && paperClassLoader.isInstance(classLoader)) {
            return ClassLoaderAppender.of(libraryLoader(paperClassLoader, classLoader), libraryManager);
        }
        if (classLoader instanceof URLClassLoader urlClassLoader) {
            return ClassLoaderAppender.of(urlClassLoader, libraryManager);
        }
        throw new IllegalStateException("Unsupported plugin class loader: " + classLoader.getClass().getName());
    }

    private static Class<?> paperPluginClassLoaderClass(ClassLoader classLoader) {
        try {
            return Class.forName(PaperClasses.PAPER_PLUGIN_CLASS_LOADER, false, classLoader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static URLClassLoader libraryLoader(Class<?> paperClassLoader, ClassLoader classLoader) {
        try {
            Field field = paperClassLoader.getDeclaredField("libraryLoader");
            field.setAccessible(true);
            Object libraryLoader = field.get(classLoader);
            if (!(libraryLoader instanceof URLClassLoader urlClassLoader)) {
                throw new IllegalStateException("PaperPluginClassLoader#libraryLoader is not a URLClassLoader: " + libraryLoader);
            }
            return urlClassLoader;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot access PaperPluginClassLoader#libraryLoader, please report this Paper version", e);
        }
    }

    @Override
    public int priority() {
        return 10;
    }
}
