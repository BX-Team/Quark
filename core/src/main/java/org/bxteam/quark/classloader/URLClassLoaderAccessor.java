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
 * instances, with fallback mechanisms for different Java versions and security configurations.
 * Available implementations include ReflectionURLClassLoaderAccessor (uses reflection to access
 * addURL method), UnsafeURLClassLoaderAccessor (uses sun.misc.Unsafe for direct field access),
 * and NoopURLClassLoaderAccessor (fallback that throws exceptions).</p>
 */
abstract class URLClassLoaderAccessor {
    /**
     * Creates an appropriate accessor for the given URLClassLoader.
     *
     * <p>This method attempts to create accessors in order of preference: reflection-based,
     * unsafe-based, and finally a no-operation fallback.</p>
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
     *
     * @param cause the underlying cause of the access failure, may be null
     * @throws ClassLoaderAccessException always thrown with descriptive message
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
     *
     * <p>This accessor uses reflection to access the protected addURL method
     * of URLClassLoader. It works on most Java versions unless restricted
     * by security policies.</p>
     */
    private static class ReflectionURLClassLoaderAccessor extends URLClassLoaderAccessor {
        private static final Method ADD_URL_METHOD = initializeAddUrlMethod();

        /**
         * Initializes the addURL method with proper error handling.
         *
         * @return the addURL method if accessible, null otherwise
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
         *
         * @return true if reflection access is available
         */
        static boolean isSupported() {
            return ADD_URL_METHOD != null;
        }

        /**
         * Creates a new reflection-based accessor.
         *
         * @param classLoader the class loader to access
         */
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
     * collections of the URLClassLoader, bypassing security restrictions.
     * Based on work by Vaishnav Anil from the SlimJar project.</p>
     */
    private static class UnsafeURLClassLoaderAccessor extends URLClassLoaderAccessor {

        private static final sun.misc.Unsafe UNSAFE = initializeUnsafe();

        /**
         * Initializes the Unsafe instance.
         *
         * @return the Unsafe instance if available, null otherwise
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
         *
         * @return true if Unsafe is available
         */
        static boolean isSupported() {
            return UNSAFE != null;
        }

        private final Collection<URL> unopenedURLs;
        private final Collection<URL> pathURLs;

        /**
         * Creates a new unsafe-based accessor.
         *
         * @param classLoader the class loader to access
         */
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
         *
         * @param clazz the class containing the field
         * @param instance the instance to get the field from
         * @param fieldName the name of the field
         * @return the field value
         * @throws NoSuchFieldException if the field does not exist
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
     *
     * <p>Used as a fallback when no other accessor can be created.
     * This implementation always fails with a descriptive error message.</p>
     */
    private static class NoopURLClassLoaderAccessor extends URLClassLoaderAccessor {
        /**
         * Creates a new no-operation accessor.
         *
         * @param classLoader the class loader (unused)
         */
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
     *
     * @since 1.0
     */
    public static class ClassLoaderAccessException extends RuntimeException {
        /**
         * Constructs a new ClassLoaderAccessException with the specified detail message.
         *
         * @param message the detail message
         */
        public ClassLoaderAccessException(String message) {
            super(message);
        }

        /**
         * Constructs a new ClassLoaderAccessException with the specified detail message and cause.
         *
         * @param message the detail message
         * @param cause the cause of this exception
         */
        public ClassLoaderAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
