package org.bxteam.quark.relocation;

import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

/**
 * Holds the downloaded ASM and the relocator classes of Quark ({@code relocation.asm}), which are compiled against
 * ASM and therefore defined here from the bytes in the plugin JAR instead of being loaded by the plugin class loader.
 */
final class RelocatorClassLoader extends IsolatedClassLoader {
    static {
        ClassLoader.registerAsParallelCapable();
    }

    /** Package of the relocator classes, derived at runtime so it follows the relocation of Quark itself. */
    static final String RELOCATOR_PACKAGE = RelocatorClassLoader.class.getPackageName() + ".asm.";

    private final ClassLoader source = RelocatorClassLoader.class.getClassLoader();

    @Override
    protected Class<?> loadClass(@NotNull String name, boolean resolve) throws ClassNotFoundException {
        if (!name.startsWith(RELOCATOR_PACKAGE)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                try (InputStream bytes = source.getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (bytes == null) {
                        throw new ClassNotFoundException(name);
                    }
                    loaded = defineClass(name, bytes);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }
}
