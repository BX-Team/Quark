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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
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
 *
 * <p>Relocated JARs are cached per relocation set: the cache key is the dependency coordinates plus a
 * hash of the relocations, see {@link #relocationHash(List)}. The same library relocated into the packages
 * of two different plugins therefore ends up in two different files, while the downloaded original is shared.</p>
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

    /**
     * Creates a new relocation handler.
     *
     * @param classLoader the isolated class loader for relocation tools
     * @param jarRelocatorConstructor the jar relocator constructor
     * @param jarRelocatorRunMethod the jar relocator run method
     */
    private RelocationHandler(@NotNull IsolatedClassLoader classLoader,
                              @NotNull Constructor<?> jarRelocatorConstructor,
                              @NotNull Method jarRelocatorRunMethod) {
        this.classLoader = requireNonNull(classLoader, "Class loader cannot be null");
        this.jarRelocatorConstructor = requireNonNull(jarRelocatorConstructor, "JAR relocator constructor cannot be null");
        this.jarRelocatorRunMethod = requireNonNull(jarRelocatorRunMethod, "JAR relocator run method cannot be null");
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

        Path relocatedJar = getRelocatedJarPath(localRepository, dependency, relocations);

        if (Files.isRegularFile(relocatedJar)) {
            return relocatedJar;
        }

        return relocate(dependency, dependencyPath, relocatedJar, relocations);
    }

    /**
     * Gets the path for a relocated JAR file, unique per dependency and relocation set.
     *
     * @param localRepository the local repository where the JAR will be stored
     * @param dependency the dependency being relocated
     * @param relocations the relocations applied to the JAR
     * @return the path to the relocated JAR
     */
    @NotNull
    public static Path getRelocatedJarPath(@NotNull LocalRepository localRepository,
                                           @NotNull Dependency dependency,
                                           @NotNull List<Relocation> relocations) {
        String classifier = dependency.getClassifier() != null ? dependency.getClassifier() + "-" : "";
        Dependency relocatedDependency = dependency.withClassifier(classifier + "relocated-" + relocationHash(relocations));
        return relocatedDependency.getJarPath(localRepository.getPath());
    }

    /**
     * Computes the cache key part for a relocation set: the first 16 hex characters of the SHA-256
     * of the relocations in order.
     *
     * @param relocations the relocations
     * @return the hash
     */
    @NotNull
    public static String relocationHash(@NotNull List<Relocation> relocations) {
        StringBuilder canonical = new StringBuilder();
        for (Relocation relocation : requireNonNull(relocations, "Relocations cannot be null")) {
            canonical.append(relocation.pattern()).append('=').append(relocation.relocatedPattern()).append('\n');
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
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

        Path temporary = null;
        try {
            Path outputParent = output.getParent();
            if (outputParent != null) {
                Files.createDirectories(outputParent);
            }

            // relocate into a temporary file first, so a crash never leaves a broken JAR in the cache
            temporary = Files.createTempFile(outputParent, output.getFileName().toString(), ".tmp");
            Files.delete(temporary);

            Object relocator = jarRelocatorConstructor.newInstance(input.toFile(), temporary.toFile(), mappings);
            jarRelocatorRunMethod.invoke(relocator);

            if (!Files.exists(temporary)) {
                throw new RelocationException("Relocation failed to create output file: " + output);
            }

            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return output;
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | IOException e) {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
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

            return new RelocationHandler(classLoader, jarRelocatorConstructor, jarRelocatorRunMethod);
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
