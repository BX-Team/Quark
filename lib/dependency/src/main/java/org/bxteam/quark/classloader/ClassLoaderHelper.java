package org.bxteam.quark.classloader;

import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.Repositories;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for managing class loaders with various access methods.
 *
 * <p>This class provides functionality to add URLs/paths to different types of class loaders
 * using reflection, Unsafe operations, or Java agents when necessary. It handles the complexity
 * of working with different Java versions and security restrictions.</p>
 */
public abstract class ClassLoaderHelper {
    /**
     * System property to set to "true" to disable the Unsafe method for initializing the class loader helper.
     */
    public static final String SYSTEM_PROPERTY_DISABLE_UNSAFE = "quark.classloaders.unsafeDisabled";

    /**
     * System property to set to "true" to disable the Java agent method for initializing the class loader helper.
     */
    public static final String SYSTEM_PROPERTY_DISABLE_JAVA_AGENT = "quark.classloaders.javaAgentDisabled";

    /**
     * Environment variable to set to "true" to disable the Unsafe method for initializing the class loader helper.
     */
    public static final String ENV_VAR_DISABLE_UNSAFE = "QUARK_CLASSLOADERS_UNSAFE_DISABLED";

    /**
     * Environment variable to set to "true" to disable the Java agent method for initializing the class loader helper.
     */
    public static final String ENV_VAR_DISABLE_JAVA_AGENT = "QUARK_CLASSLOADERS_JAVA_AGENT_DISABLED";

    /**
     * net.bytebuddy.agent.ByteBuddyAgent class name for reflections
     */
    private static final String BYTE_BUDDY_AGENT_CLASS = "net.bytebuddy.agent.ByteBuddyAgent";

    /**
     * java.lang.Module methods since we build against Java 8
     */
    private static final Method getModuleMethod, addOpensMethod, getNameMethod;

    static {
        Method getModule = null, addOpens = null, getName = null;
        try {
            Class<?> moduleClass = Class.forName("java.lang.Module");
            getModule = Class.class.getMethod("getModule");
            addOpens = moduleClass.getMethod("addOpens", String.class, moduleClass);
            getName = moduleClass.getMethod("getName");
        } catch (Exception ignored) {
        } finally {
            getModuleMethod = getModule;
            addOpensMethod = addOpens;
            getNameMethod = getName;
        }
    }

    /**
     * Unsafe class instance. Used in {@link #getPrivilegedMethodHandle(Method)}.
     */
    private static final Unsafe theUnsafe;

    static {
        Unsafe unsafe = null;

        for (Field f : Unsafe.class.getDeclaredFields()) {
            try {
                if (f.getType() == Unsafe.class && Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    unsafe = (Unsafe) f.get(null);
                }
            } catch (Exception ignored) {
            }
        }
        theUnsafe = unsafe;
    }

    /**
     * Cached {@link Instrumentation} instance. Used by {@link #initInstrumentation(LibraryManager)}.
     */
    private static volatile Instrumentation cachedInstrumentation;

    /**
     * The class loader being managed by this helper.
     */
    protected final ClassLoader classLoader;

    /**
     * Creates a new class loader helper.
     *
     * @param classLoader the class loader to manage
     */
    public ClassLoaderHelper(@NotNull ClassLoader classLoader) {
        this.classLoader = requireNonNull(classLoader, "classLoader");
    }

    /**
     * Adds a URL to the class loader's classpath.
     *
     * @param url the URL to add
     */
    public abstract void addToClasspath(@NotNull URL url);

    /**
     * Adds a path to the class loader's classpath.
     *
     * @param path the path to add
     */
    public void addToClasspath(@NotNull Path path) {
        try {
            addToClasspath(requireNonNull(path, "path").toUri().toURL());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Sets the method accessible using reflections, the Unsafe class or a java agent loaded at runtime.
     * <p>The provided consumers are mutually exclusive, i.e. only 1 of the provided consumers will run (if run).
     *
     * @param libraryManager the {@link LibraryManager}
     * @param method the method to set accessible
     * @param methodSignature the signature of the method, used in error messages
     * @param methodHandleConsumer a {@link Consumer} which might get called with a {@link MethodHandle} to the method
     * @param instrumentationConsumer a {@link Consumer} which might get called with an {@link Instrumentation} instance
     */
    protected void setMethodAccessible(@NotNull LibraryManager libraryManager, @NotNull Method method, @NotNull String methodSignature,
                                      @NotNull Consumer<MethodHandle> methodHandleConsumer, @NotNull Consumer<Instrumentation> instrumentationConsumer) {
        if (Modifier.isPublic(method.getModifiers())) {
            return;
        }

        try {
            openModule(method.getDeclaringClass());
        } catch (Exception ignored) {}

        try {
            method.setAccessible(true);
            return;
        } catch (Exception e) {
            handleInaccessibleObjectException(e, methodSignature);
        }

        Exception unsafeException = null;
        unsafe: if (theUnsafe != null && canUseUnsafe()) {
            MethodHandle methodHandle;
            try {
                methodHandle = getPrivilegedMethodHandle(method).bindTo(classLoader);
            } catch (Exception e) {
                unsafeException = e;
                break unsafe;
            }
            methodHandleConsumer.accept(methodHandle);
            return;
        }

        Exception javaAgentException = null;
        javaAgent: if (canUseJavaAgent()) {
            Instrumentation instrumentation;
            try {
                instrumentation = initInstrumentation(libraryManager);
            } catch (Exception e) {
                javaAgentException = e;
                break javaAgent;
            }
            try {
                instrumentationConsumer.accept(instrumentation);
                return;
            } catch (Exception e) {
                handleInaccessibleObjectException(e, methodSignature);
            }
        }

        Logger logger = libraryManager.getLogger();
        if (unsafeException != null) {
            logger.error("Cannot set accessible " + methodSignature + " using unsafe", unsafeException);
        }
        if (javaAgentException != null) {
            logger.error("Cannot set accessible " + methodSignature + " using java agent", javaAgentException);
        }

        String packageName = method.getDeclaringClass().getPackage().getName();
        String moduleName = null;
        try {
            moduleName = (String) getNameMethod.invoke(getModuleMethod.invoke(method.getDeclaringClass()));
        } catch (Exception ignored) {}

        if (moduleName != null) {
            logger.error("Cannot set accessible " + methodSignature + ", if you are using Java 9+ try to add the following option to your java command: --add-opens " + moduleName + "/" + packageName + "=ALL-UNNAMED");
        } else {
            logger.error("Cannot set accessible " + methodSignature);
        }

        throw new RuntimeException("Cannot set accessible " + methodSignature);
    }

    private void handleInaccessibleObjectException(@NotNull Exception exception, @NotNull String methodSignature) {
        if (!exception.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
            throw new RuntimeException("Cannot set accessible "+ methodSignature, exception);
        }
    }

    /**
     * Opens the module of the provided class using reflections.
     *
     * @param toOpen The class
     * @throws Exception if an error occurs
     */
    protected static void openModule(@NotNull Class<?> toOpen) throws Exception {
        Object urlClassLoaderModule = getModuleMethod.invoke(toOpen);
        Object thisModule = getModuleMethod.invoke(ClassLoaderHelper.class);

        addOpensMethod.invoke(urlClassLoaderModule, toOpen.getPackage().getName(), thisModule);
    }

    /**
     * Try to get a MethodHandle for the given method.
     *
     * @param method the method to get the handle for
     * @return the method handle
     */
    protected MethodHandle getPrivilegedMethodHandle(@NotNull Method method) {
        for (Field trustedLookup : MethodHandles.Lookup.class.getDeclaredFields()) {
            if (trustedLookup.getType() != MethodHandles.Lookup.class || !Modifier.isStatic(trustedLookup.getModifiers()) || trustedLookup.isSynthetic())
                continue;

            try {
                MethodHandles.Lookup lookup = (MethodHandles.Lookup) theUnsafe.getObject(theUnsafe.staticFieldBase(trustedLookup), theUnsafe.staticFieldOffset(trustedLookup));
                return lookup.unreflect(method);
            } catch (Exception ignored) { }
        }

        throw new RuntimeException("Cannot get privileged method handle.");
    }

    /**
     * Load ByteBuddy agent and return an {@link Instrumentation} instance.
     *
     * @param libraryManager the library manager used to download dependencies
     * @return an {@link Instrumentation} instance
     * @throws Exception if an error occurs
     */
    protected Instrumentation initInstrumentation(@NotNull LibraryManager libraryManager) throws Exception {
        Instrumentation instr = cachedInstrumentation;
        if (instr != null) {
            return instr;
        }

        try (IsolatedClassLoader isolatedClassLoader = new IsolatedClassLoader()) {
            Dependency byteBuddyAgent = Dependency.of("net.bytebuddy", "byte-buddy", "1.12.1");

            libraryManager.addRepository(Repositories.GOOGLE_MAVEN_CENTRAL_MIRROR);

            libraryManager.loadDependencies(isolatedClassLoader, java.util.Collections.singletonList(byteBuddyAgent), java.util.Collections.emptyList());

            Class<?> byteBuddyAgentClass = isolatedClassLoader.loadClass(BYTE_BUDDY_AGENT_CLASS);

            Instrumentation instrumentation = (Instrumentation) byteBuddyAgentClass.getMethod("install").invoke(null);
            cachedInstrumentation = instrumentation;
            return instrumentation;
        }
    }

    /**
     * Checks if the Unsafe method can be used to initialize the class loader helper.
     *
     * @return {@code true} if either the system property {@link #SYSTEM_PROPERTY_DISABLE_UNSAFE} or the environment
     *         variable {@link #ENV_VAR_DISABLE_UNSAFE} are set to {@code "true"}, {@code false} otherwise.
     * @see #setMethodAccessible(LibraryManager, Method, String, Consumer, Consumer)
     */
    protected boolean canUseUnsafe() {
        return !Boolean.parseBoolean(System.getProperty(SYSTEM_PROPERTY_DISABLE_UNSAFE)) && !Boolean.parseBoolean(System.getenv(ENV_VAR_DISABLE_UNSAFE));
    }

    /**
     * Checks if the Java agent method can be used to initialize the class loader helper.
     *
     * @return {@code true} if either the system property {@link #SYSTEM_PROPERTY_DISABLE_JAVA_AGENT} or the environment
     *         variable {@link #ENV_VAR_DISABLE_JAVA_AGENT} are set to {@code "true"}, {@code false} otherwise.
     * @see #setMethodAccessible(LibraryManager, Method, String, Consumer, Consumer)
     */
    protected boolean canUseJavaAgent() {
        return !Boolean.parseBoolean(System.getProperty(SYSTEM_PROPERTY_DISABLE_JAVA_AGENT)) && !Boolean.parseBoolean(System.getenv(ENV_VAR_DISABLE_JAVA_AGENT));
    }
}
