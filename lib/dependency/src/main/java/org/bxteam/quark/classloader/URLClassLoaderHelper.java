package org.bxteam.quark.classloader;

import org.bxteam.quark.LibraryManager;
import org.jetbrains.annotations.NotNull;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Helper class for adding JARs to URLClassLoader instances.
 *
 * <p>This class provides functionality to add URLs to URLClassLoader's
 * classpath using reflection to access the protected addURL method or
 * using Java agents when necessary.</p>
 */
public class URLClassLoaderHelper extends ClassLoaderHelper {
    private MethodHandle addURLMethodHandle = null;

    /**
     * Creates a new URL class loader helper.
     *
     * @param classLoader the class loader to manage
     * @param libraryManager the library manager used to download dependencies
     */
    public URLClassLoaderHelper(@NotNull URLClassLoader classLoader, @NotNull LibraryManager libraryManager) {
        super(classLoader);
        requireNonNull(libraryManager, "libraryManager");

        try {
            Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            setMethodAccessible(libraryManager, addURLMethod, "URLClassLoader#addURL(URL)",
                    methodHandle -> {
                        addURLMethodHandle = methodHandle;
                    },
                    instrumentation -> {
                        addOpensWithAgent(instrumentation);
                        addURLMethod.setAccessible(true);
                    }
            );
            if (addURLMethodHandle == null) {
                addURLMethodHandle = MethodHandles.lookup().unreflect(addURLMethod).bindTo(classLoader);
            }
        } catch (Exception e) {
            throw new RuntimeException("Couldn't initialize URLClassLoaderHelper", e);
        }
    }

    @Override
    public void addToClasspath(@NotNull URL url) {
        requireNonNull(url, "url");

        try {
            addURLMethodHandle.invokeWithArguments(url);
        } catch (Throwable e) {
            throw new RuntimeException("Failed to add URL to URLClassLoader: " + url, e);
        }
    }

    /**
     * Opens the java.net module using a Java agent to allow access to URLClassLoader internals.
     *
     * @param instrumentation the instrumentation instance from the Java agent
     */
    private void addOpensWithAgent(@NotNull Instrumentation instrumentation) {
        try {
            Method redefineModule = Instrumentation.class.getMethod("redefineModule", Class.forName("java.lang.Module"), Set.class, Map.class, Map.class, Set.class, Map.class);
            Method getModule = Class.class.getMethod("getModule");
            Map<String, Set<?>> toOpen = Collections.singletonMap("java.net", Collections.singleton(getModule.invoke(getClass())));
            redefineModule.invoke(instrumentation, getModule.invoke(URLClassLoader.class), Collections.emptySet(), Collections.emptyMap(), toOpen, Collections.emptySet(), Collections.emptyMap());
        } catch (Exception e) {
            throw new RuntimeException("Failed to open java.net module with agent", e);
        }
    }
}
