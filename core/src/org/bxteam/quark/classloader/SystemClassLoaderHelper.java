package org.bxteam.quark.classloader;

import org.bxteam.quark.LibraryManager;
import org.jetbrains.annotations.NotNull;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.jar.JarFile;

import static java.util.Objects.requireNonNull;

/**
 * Helper class for adding JARs to the system class loader.
 *
 * <p>This class provides functionality to add URLs to the system class loader's
 * classpath using reflection to access the protected appendToClassPathForInstrumentation
 * method or using Java agents when necessary.</p>
 */
public class SystemClassLoaderHelper extends ClassLoaderHelper {
    private MethodHandle appendMethodHandle = null;
    private Instrumentation appendInstrumentation = null;

    /**
     * Creates a new SystemClassLoader helper.
     *
     * @param classLoader the class loader to manage
     * @param libraryManager the library manager used to download dependencies
     */
    public SystemClassLoaderHelper(@NotNull ClassLoader classLoader, @NotNull LibraryManager libraryManager) {
        super(classLoader);
        requireNonNull(libraryManager, "libraryManager");

        try {
            Method appendMethod = classLoader.getClass().getDeclaredMethod("appendToClassPathForInstrumentation", String.class);
            setMethodAccessible(libraryManager, appendMethod, classLoader.getClass().getName() + "#appendToClassPathForInstrumentation(String)",
                    methodHandle -> {
                        appendMethodHandle = methodHandle;
                    },
                    instrumentation -> {
                        appendInstrumentation = instrumentation;
                    }
            );
        } catch (Exception e) {
            throw new RuntimeException("Couldn't initialize SystemClassLoaderHelper", e);
        }
    }

    @Override
    public void addToClasspath(@NotNull URL url) {
        requireNonNull(url, "url");

        try {
            if (appendInstrumentation != null) {
                appendInstrumentation.appendToSystemClassLoaderSearch(new JarFile(url.toURI().getPath()));
            } else if (appendMethodHandle != null) {
                appendMethodHandle.invokeWithArguments(url.toURI().getPath());
            } else {
                throw new IllegalStateException("No method available to add to classpath");
            }
        } catch (Throwable e) {
            throw new RuntimeException("Failed to add URL to system classpath: " + url, e);
        }
    }
}
