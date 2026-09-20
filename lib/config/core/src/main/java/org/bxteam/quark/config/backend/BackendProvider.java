package org.bxteam.quark.config.backend;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Decides where a {@link Backend} comes from, in this order:
 *
 * <ol>
 *     <li>it is already on the class path (shaded into the plugin, or provided by the server): use it;</li>
 *     <li>{@code quark-dependency} is on the class path: download the library, relocate it to the package the
 *     shaded code expects and add it to the plugin class path;</li>
 *     <li>otherwise fail with a {@link BackendUnavailableException} that says what to add.</li>
 * </ol>
 *
 * <p>For step 2 the library manager registered with {@link #libraryManager(Object)} is used. Without one, a
 * manager is created on first use: it stores JARs in {@code libs/} under the directory of the configuration
 * file and adds them to the class loader that loaded Quark.</p>
 *
 * <p>Quark is relocated into every plugin, so this state is per plugin.</p>
 */
public final class BackendProvider {
    private static final String LIBRARY_MANAGER_CLASS = "org.bxteam.quark.LibraryManager";
    private static final Set<String> AVAILABLE = ConcurrentHashMap.newKeySet();
    private static volatile Object libraryManager;

    private BackendProvider() {
    }

    /**
     * Uses this library manager to download backends, for example the one the plugin already has. The manager
     * must be able to add JARs to the plugin class path (built with {@code plugin(...)}, {@code classLoader(...)} or
     * {@code appender(...)}).
     *
     * @param manager an {@code org.bxteam.quark.LibraryManager}; typed as Object so this class loads without
     *                {@code quark-dependency}
     * @throws IllegalArgumentException if the object is not a library manager
     */
    public static void libraryManager(@NotNull Object manager) {
        requireNonNull(manager, "Library manager cannot be null");
        if (!isDependencyModulePresent() || !DependencyBackendLoader.isLibraryManager(manager)) {
            throw new IllegalArgumentException("Expected an org.bxteam.quark.LibraryManager, got " + manager.getClass().getName());
        }
        libraryManager = manager;
    }

    /**
     * Makes sure a backend can be used.
     *
     * @param backend the backend
     * @param dataDirectory where a library manager created by Quark stores downloaded JARs ({@code libs/} inside)
     * @throws BackendUnavailableException if the backend is not on the class path and cannot be downloaded
     */
    public static void ensure(@NotNull Backend backend, @NotNull Path dataDirectory) {
        requireNonNull(backend, "Backend cannot be null");
        requireNonNull(dataDirectory, "Data directory cannot be null");
        if (AVAILABLE.contains(backend.probeClass())) {
            return;
        }
        synchronized (BackendProvider.class) {
            if (AVAILABLE.contains(backend.probeClass())) {
                return;
            }
            if (!isLoaded(backend)) {
                if (!isDependencyModulePresent()) {
                    throw new BackendUnavailableException(backend.name() + " (" + backend.coordinates() + ") is not on the class path. "
                            + "Shade it into the plugin, or add quark-dependency (quark { modules(QuarkModule.DEPENDENCY) }) "
                            + "so Quark downloads it at runtime.");
                }
                try {
                    libraryManager = DependencyBackendLoader.load(backend, libraryManager, dataDirectory);
                } catch (RuntimeException | LinkageError e) {
                    throw new BackendUnavailableException("Could not download " + backend.name() + " (" + backend.coordinates()
                            + "): " + e.getMessage() + ". Check the network access of the server or shade the library into the plugin.", e);
                }
                if (!isLoaded(backend)) {
                    throw new BackendUnavailableException(backend.name() + " was downloaded but " + backend.probeClass()
                            + " is still not visible to " + BackendProvider.class.getClassLoader()
                            + ". Register a library manager that adds JARs to the plugin class path with BackendProvider.libraryManager(...)");
                }
            }
            AVAILABLE.add(backend.probeClass());
        }
    }

    /**
     * @return true if {@code quark-dependency} is on the class path
     */
    public static boolean isDependencyModulePresent() {
        return classExists(LIBRARY_MANAGER_CLASS);
    }

    private static boolean isLoaded(Backend backend) {
        return classExists(backend.probeClass());
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, BackendProvider.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
