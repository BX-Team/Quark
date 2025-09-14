package org.bxteam.quark.relocation;

import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.repository.LocalRepository;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import static java.util.Objects.requireNonNull;

/**
 * Handles runtime relocation of packages in JAR dependencies.
 *
 * <p>This class uses the jar-relocator library to rename packages in JAR files
 * to avoid conflicts between different versions of the same library. It creates
 * an isolated class loader to load the relocation tools and caches relocated
 * JARs to avoid repeated processing.</p>
 */
public class RelocationHandler {
    private static final List<Dependency> RELOCATION_DEPENDENCIES = List.of(
            Dependency.of("org.ow2.asm", "asm", "9.7"),
            Dependency.of("org.ow2.asm", "asm-commons", "9.7"),
            Dependency.of("me.lucko", "jar-relocator", "1.7")
    );

    private static final String JAR_RELOCATOR_CLASS = "me.lucko.jarrelocator.JarRelocator";
    private static final String JAR_RELOCATOR_RUN_METHOD = "run";

    private final IsolatedClassLoader classLoader;
    private final Constructor<?> jarRelocatorConstructor;
    private final Method jarRelocatorRunMethod;
    private final RelocationCacheResolver cacheResolver;

    /**
     * Creates a new relocation handler.
     *
     * @param classLoader the isolated class loader for relocation tools
     * @param jarRelocatorConstructor the jar relocator constructor
     * @param jarRelocatorRunMethod the jar relocator run method
     * @param cacheResolver the cache resolver for tracking relocations
     */
    private RelocationHandler(@NotNull IsolatedClassLoader classLoader,
                              @NotNull Constructor<?> jarRelocatorConstructor,
                              @NotNull Method jarRelocatorRunMethod,
                              @NotNull RelocationCacheResolver cacheResolver) {
        this.classLoader = requireNonNull(classLoader, "Class loader cannot be null");
        this.jarRelocatorConstructor = requireNonNull(jarRelocatorConstructor, "JAR relocator constructor cannot be null");
        this.jarRelocatorRunMethod = requireNonNull(jarRelocatorRunMethod, "JAR relocator run method cannot be null");
        this.cacheResolver = requireNonNull(cacheResolver, "Cache resolver cannot be null");
    }

    /**
     * Relocates a dependency JAR if relocations are specified.
     *
     * @param localRepository the local repository for storing relocated JARs
     * @param dependencyPath the path to the original JAR
     * @param dependency the dependency being relocated
     * @param relocations the list of relocations to apply
     * @return the path to the relocated JAR (or original if no relocations)
     * @throws RelocationException if relocation fails
     */
    @NotNull
    public Path relocateDependency(@NotNull LocalRepository localRepository,
                                   @NotNull Path dependencyPath,
                                   @NotNull Dependency dependency,
                                   @NotNull List<Relocation> relocations) {
        requireNonNull(localRepository, "Local repository cannot be null");
        requireNonNull(dependencyPath, "Dependency path cannot be null");
        requireNonNull(dependency, "Dependency cannot be null");
        requireNonNull(relocations, "Relocations cannot be null");

        if (relocations.isEmpty()) {
            return dependencyPath;
        }

        Path relocatedJar = getRelocatedJarPath(localRepository, dependency);

        if (Files.exists(relocatedJar) && !cacheResolver.shouldForceRelocate(dependency, relocations)) {
            return relocatedJar;
        }

        return relocate(dependency, dependencyPath, relocatedJar, relocations);
    }

    /**
     * Gets the path for a relocated JAR file.
     *
     * @param localRepository the local repository where the JAR will be stored
     * @param dependency the dependency being relocated
     * @return the path to the relocated JAR
     */
    @NotNull
    private Path getRelocatedJarPath(@NotNull LocalRepository localRepository, @NotNull Dependency dependency) {
        Dependency relocatedDependency = dependency.withClassifier("relocated");
        return relocatedDependency.getJarPath(localRepository.getPath());
    }

    /**
     * Performs the actual JAR relocation using reflection.
     *
     * @param dependency the dependency being relocated
     * @param input the input JAR path
     * @param output the output JAR path
     * @param relocations the relocations to apply
     * @return the path to the relocated JAR
     * @throws RelocationException if relocation fails
     */
    @NotNull
    private Path relocate(@NotNull Dependency dependency, @NotNull Path input, @NotNull Path output, @NotNull List<Relocation> relocations) {
        Map<String, String> mappings = new HashMap<>();

        for (Relocation relocation : relocations) {
            mappings.put(relocation.pattern(), relocation.relocatedPattern());
        }

        try {
            Path outputParent = output.getParent();
            if (outputParent != null) {
                Files.createDirectories(outputParent);
            }

            Files.deleteIfExists(output);

            Object relocator = jarRelocatorConstructor.newInstance(input.toFile(), output.toFile(), mappings);
            jarRelocatorRunMethod.invoke(relocator);

            cacheResolver.markAsRelocated(dependency, relocations);

            if (!Files.exists(output)) {
                throw new RelocationException("Relocation failed to create output file: " + output);
            }

            return output;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | IOException e) {
            throw new RelocationException("Failed to relocate JAR for dependency: " + dependency.toShortString(), e);
        }
    }

    /**
     * Creates a new relocation handler by loading the required dependencies.
     *
     * @param libraryManager the library manager to load dependencies with
     * @return a new relocation handler
     * @throws RelocationException if the handler cannot be created
     */
    @NotNull
    public static RelocationHandler create(@NotNull LibraryManager libraryManager) {
        requireNonNull(libraryManager, "Library manager cannot be null");

        IsolatedClassLoader classLoader = new IsolatedClassLoader();

        try {
            libraryManager.loadDependencies(classLoader, RELOCATION_DEPENDENCIES, Collections.emptyList());

            Class<?> jarRelocatorClass = classLoader.loadClass(JAR_RELOCATOR_CLASS);

            Constructor<?> jarRelocatorConstructor = jarRelocatorClass.getDeclaredConstructor(File.class, File.class, Map.class);
            jarRelocatorConstructor.setAccessible(true);

            Method jarRelocatorRunMethod = jarRelocatorClass.getDeclaredMethod(JAR_RELOCATOR_RUN_METHOD);
            jarRelocatorRunMethod.setAccessible(true);

            RelocationCacheResolver cacheResolver = new RelocationCacheResolver(libraryManager.getLocalRepository());

            return new RelocationHandler(classLoader, jarRelocatorConstructor, jarRelocatorRunMethod, cacheResolver);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new RelocationException("Failed to initialize relocation handler", e);
        }
    }

    /**
     * Gets the relocation dependencies that need to be loaded.
     *
     * @return list of relocation dependencies
     */
    @NotNull
    public static List<Dependency> getRelocationDependencies() {
        return List.copyOf(RELOCATION_DEPENDENCIES);
    }

    @Override
    public String toString() {
        return "RelocationHandler{" +
                "classLoader=" + classLoader.getClass().getSimpleName() +
                '}';
    }

    /**
     * Exception thrown when relocation operations fail.
     */
    public static class RelocationException extends RuntimeException {
        public RelocationException(String message) {
            super(message);
        }

        public RelocationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
