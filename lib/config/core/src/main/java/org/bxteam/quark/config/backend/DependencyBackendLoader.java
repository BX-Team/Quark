package org.bxteam.quark.config.backend;

import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.common.JulLogger;
import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.relocation.Relocation;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;

/**
 * The part of {@link BackendProvider} that touches {@code quark-dependency}. Only loaded after the provider checked
 * that the module is on the class path.
 */
final class DependencyBackendLoader {
    private static final String GOOGLE_MAVEN_CENTRAL_MIRROR = "https://maven-central.storage-download.googleapis.com/maven2/";

    private DependencyBackendLoader() {
    }

    static boolean isLibraryManager(Object object) {
        return object instanceof LibraryManager;
    }

    /**
     * Downloads the backend with the given manager, or with a new one if it is null.
     *
     * @return the manager used
     */
    static Object load(Backend backend, Object manager, Path dataDirectory) {
        LibraryManager libraryManager = manager != null ? (LibraryManager) manager : create(dataDirectory);
        Dependency dependency = Dependency.fromCoordinates(backend.coordinates())
                .withFallbackRepository(GOOGLE_MAVEN_CENTRAL_MIRROR);
        List<Relocation> relocations = backend.relocated()
                ? List.of(Relocation.of(backend.originalPackage(), backend.relocatedPackage()))
                : List.of();
        libraryManager.loadDependencies(List.of(dependency), relocations);
        return libraryManager;
    }

    private static LibraryManager create(Path dataDirectory) {
        ClassLoader classLoader = DependencyBackendLoader.class.getClassLoader();
        if (!(classLoader instanceof URLClassLoader urlClassLoader)) {
            throw new IllegalStateException("cannot add JARs to " + classLoader.getClass().getName()
                    + ", register a library manager with BackendProvider.libraryManager(...)");
        }
        LibraryManager libraryManager = LibraryManager.builder()
                .dataDirectory(dataDirectory)
                .classLoader(urlClassLoader)
                .logger(JulLogger.of("Quark"))
                .build();
        if (libraryManager.getRepositories().isEmpty()) {
            libraryManager.addGoogleMavenCentralMirror();
        }
        return libraryManager;
    }
}
