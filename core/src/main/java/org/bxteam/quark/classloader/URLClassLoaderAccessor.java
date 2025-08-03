package org.bxteam.quark.classloader;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;

import static java.util.Objects.requireNonNull;

/**
 * Abstract accessor for modifying URLClassLoader instances using reflection or unsafe operations.
 *
 * <p>This class provides different strategies for adding URLs to existing URLClassLoader
 * instances, with fallback mechanisms for different Java versions and security configurations:</p>
 * <ul>
 *   <li>{@link ReflectionURLClassLoaderAccessor} - Uses reflection to access addURL method</li>
 *   <li>{@link UnsafeURLClassLoaderAccessor} - Uses sun.misc.Unsafe for direct field access</li>
 *   <li>{@link NoopURLClassLoaderAccessor} - Fallback that throws exceptions</li>
 * </ul>
 */
abstract class URLClassLoaderAccessor {
    /**
     * Creates an appropriate accessor for the given URLClassLoader.
     *
     * @param classLoader the class loader to create an accessor for
     * @return a new accessor instance
     * @throws NullPointerException if classLoader is null
     */
    @NotNull
    public static URLClassLoaderAccessor create(@NotNull URLClassLoader classLoader) {
        requireNonNull(classLoader, "Class loader cannot be null");

        if (ReflectionURLClassLoaderAccessor.isSupported()) {
            return new ReflectionURLClassLoaderAccessor(classLoader);
        }

        if (UnsafeURLClassLoaderAccessor.isSupported()) {
            return new UnsafeURLClassLoaderAccessor(classLoader);
        }

        return new NoopURLClassLoaderAccessor(classLoader);
    }

    protected final URLClassLoader classLoader;

    /**
     * Creates a new accessor for the specified class loader.
     *
     * @param classLoader the class loader to access
     */
    protected URLClassLoaderAccessor(@NotNull URLClassLoader classLoader) {
        this.classLoader = requireNonNull(classLoader, "Class loader cannot be null");
    }

    /**
     * Adds a URL to the class loader's classpath.
     *
     * @param url the URL to add
     * @throws ClassLoaderAccessException if the URL cannot be added
     */
    public abstract void addURL(@NotNull URL url);

    /**
     * Adds a JAR file to the class loader's classpath.
     *
     * @param jarPath the path to the JAR file
     * @throws ClassLoaderAccessException if the JAR cannot be added
     * @throws NullPointerException if jarPath is null
     */
    public void addJarToClasspath(@NotNull Path jarPath) {
        requireNonNull(jarPath, "JAR path cannot be null");

        try {
            addURL(jarPath.toUri().toURL());
        } catch (MalformedURLException e) {
            throw new ClassLoaderAccessException("Invalid JAR path: " + jarPath, e);
        }
    }

    /**
     * Gets the type name of this accessor.
     *
     * @return the accessor type name
     */
    @NotNull
    public String getType() {
        return getClass().getSimpleName();
    }

    /**
     * Throws a descriptive error when class loader access fails.
     */
    protected static void throwAccessError(@Nullable Throwable cause) {
        String message = "Quark is unable to inject JARs into the URLClassLoader.\n" +
                "You may be able to fix this problem by adding the following JVM argument:\n" +
                "--add-opens java.base/java.lang=ALL-UNNAMED\n" +
                "Alternatively, try using a different class loader implementation.";

        throw new ClassLoaderAccessException(message, cause);
    }

    /**
     * Reflection-based accessor that uses the addURL method.
     */
    private static class ReflectionURLClassLoaderAccessor extends URLClassLoaderAccessor {
        private static final Method ADD_URL_METHOD = initializeAddUrlMethod();

        /**
         * Initializes the addURL method with proper error handling.
         */
        @Nullable
        private static Method initializeAddUrlMethod() {
            try {
                Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                method.setAccessible(true);
                return method;
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * Checks if reflection-based access is supported.
         */
        static boolean isSupported() {
            return ADD_URL_METHOD != null;
        }

        ReflectionURLClassLoaderAccessor(@NotNull URLClassLoader classLoader) {
            super(classLoader);
        }

        @Override
        public void addURL(@NotNull URL url) {
            requireNonNull(url, "URL cannot be null");

            try {
                ADD_URL_METHOD.invoke(classLoader, url);
            } catch (ReflectiveOperationException e) {
                throwAccessError(e);
            }
        }
    }

    /**
     * Unsafe-based accessor for Java 9+ environments.
     *
     * <p>This accessor uses sun.misc.Unsafe to directly modify the internal
     * collections of the URLClassLoader, bypassing security restrictions.</p>
     *
     * @author Based on work by Vaishnav Anil (SlimJar project)
     */
    private static class UnsafeURLClassLoaderAccessor extends URLClassLoaderAccessor {

        private static final sun.misc.Unsafe UNSAFE = initializeUnsafe();

        /**
         * Initializes the Unsafe instance.
         */
        @Nullable
        private static sun.misc.Unsafe initializeUnsafe() {
            try {
                Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                return (sun.misc.Unsafe) unsafeField.get(null);
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * Checks if unsafe-based access is supported.
         */
        static boolean isSupported() {
            return UNSAFE != null;
        }

        private final Collection<URL> unopenedURLs;
        private final Collection<URL> pathURLs;

        @SuppressWarnings("unchecked")
        UnsafeURLClassLoaderAccessor(@NotNull URLClassLoader classLoader) {
            super(classLoader);

            Collection<URL> unopenedURLs = null;
            Collection<URL> pathURLs = null;

            try {
                Object urlClassPath = getFieldValue(URLClassLoader.class, classLoader, "ucp");

                unopenedURLs = (Collection<URL>) getFieldValue(urlClassPath.getClass(), urlClassPath, "unopenedUrls");
                pathURLs = (Collection<URL>) getFieldValue(urlClassPath.getClass(), urlClassPath, "path");

            } catch (Exception e) { }

            this.unopenedURLs = unopenedURLs;
            this.pathURLs = pathURLs;
        }

        /**
         * Gets a field value using Unsafe.
         */
        @Nullable
        private Object getFieldValue(@NotNull Class<?> clazz, @NotNull Object instance, @NotNull String fieldName)
                throws NoSuchFieldException {
            Field field = clazz.getDeclaredField(fieldName);
            long offset = UNSAFE.objectFieldOffset(field);
            return UNSAFE.getObject(instance, offset);
        }

        @Override
        public void addURL(@NotNull URL url) {
            requireNonNull(url, "URL cannot be null");

            if (unopenedURLs == null || pathURLs == null) {
                throwAccessError(new IllegalStateException("Unsafe accessor not properly initialized"));
            }

            synchronized (unopenedURLs) {
                unopenedURLs.add(url);
                pathURLs.add(url);
            }
        }
    }

    /**
     * No-operation accessor that always throws exceptions.
     * Used as a fallback when no other accessor can be created.
     */
    private static class NoopURLClassLoaderAccessor extends URLClassLoaderAccessor {
        NoopURLClassLoaderAccessor(@NotNull URLClassLoader classLoader) {
            super(classLoader);
        }

        @Override
        public void addURL(@NotNull URL url) {
            throwAccessError(null);
        }
    }

    /**
     * Exception thrown when class loader access operations fail.
     */
    public static class ClassLoaderAccessException extends RuntimeException {
        public ClassLoaderAccessException(String message) {
            super(message);
        }

        public ClassLoaderAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
