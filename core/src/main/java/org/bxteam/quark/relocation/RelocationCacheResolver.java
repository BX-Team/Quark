package org.bxteam.quark.relocation;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.repository.LocalRepository;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Resolves whether relocated JARs need to be regenerated based on relocation changes.
 *
 * <p>This class tracks the relocations applied to each dependency and determines
 * whether a cached relocated JAR is still valid or needs to be regenerated
 * due to changes in the relocation rules.</p>
 */
public class RelocationCacheResolver {

    private static final String RELOCATIONS_CACHE_FILE = "relocations.txt";

    private final LocalRepository localRepository;

    /**
     * Creates a new relocation cache resolver.
     *
     * @param localRepository the local repository for cache storage
     * @throws NullPointerException if localRepository is null
     */
    public RelocationCacheResolver(@NotNull LocalRepository localRepository) {
        this.localRepository = requireNonNull(localRepository, "Local repository cannot be null");
    }

    /**
     * Determines whether a dependency should be forcibly relocated.
     *
     * <p>Returns true if:</p>
     * <ul>
     *   <li>No cached relocation information exists</li>
     *   <li>The relocations have changed since last time</li>
     * </ul>
     *
     * @param dependency the dependency to check
     * @param relocations the current relocations to apply
     * @return true if the dependency should be relocated
     */
    public boolean shouldForceRelocate(@NotNull Dependency dependency, @NotNull List<Relocation> relocations) {
        requireNonNull(dependency, "Dependency cannot be null");
        requireNonNull(relocations, "Relocations cannot be null");

        return getSavedRelocations(dependency)
                .map(savedRelocations -> !savedRelocations.equals(relocationsToString(relocations)))
                .orElse(true);
    }

    /**
     * Marks a dependency as relocated with the given relocations.
     *
     * @param dependency the dependency that was relocated
     * @param relocations the relocations that were applied
     * @throws RuntimeException if saving fails
     */
    public void markAsRelocated(@NotNull Dependency dependency, @NotNull List<Relocation> relocations) {
        requireNonNull(dependency, "Dependency cannot be null");
        requireNonNull(relocations, "Relocations cannot be null");

        try {
            Path cacheFile = getRelocationsCacheFile(dependency);
            String relocationsString = relocationsToString(relocations);

            // Ensure parent directory exists
            Path parentDir = cacheFile.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            // Write relocations to cache file
            Files.writeString(cacheFile, relocationsString, StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new RuntimeException("Failed to save relocation cache for dependency: " + dependency, e);
        }
    }

    /**
     * Converts relocations to a string representation.
     */
    @NotNull
    private String relocationsToString(@NotNull List<Relocation> relocations) {
        return relocations.stream()
                .map(relocation -> relocation.pattern() + " -> " + relocation.relocatedPattern())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Gets the saved relocations for a dependency.
     */
    @NotNull
    private Optional<String> getSavedRelocations(@NotNull Dependency dependency) {
        try {
            Path cacheFile = getRelocationsCacheFile(dependency);

            if (!Files.exists(cacheFile)) {
                return Optional.empty();
            }

            String content = Files.readString(cacheFile, StandardCharsets.UTF_8);
            return Optional.of(content.trim());

        } catch (IOException e) {
            // If we can't read the cache, assume we need to relocate
            return Optional.empty();
        }
    }

    /**
     * Gets the path to the relocations cache file for a dependency.
     */
    @NotNull
    private Path getRelocationsCacheFile(@NotNull Dependency dependency) {
        // Create a path within the dependency's directory
        String relativePath = dependency.getGroupId().replace('.', '/') + '/' +
                dependency.getArtifactId() + '/' +
                dependency.getVersion() + '/' +
                RELOCATIONS_CACHE_FILE;

        return localRepository.resolve(relativePath);
    }

    /**
     * Clears the relocation cache for a specific dependency.
     *
     * @param dependency the dependency to clear cache for
     */
    public void clearCache(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");

        try {
            Path cacheFile = getRelocationsCacheFile(dependency);
            Files.deleteIfExists(cacheFile);
        } catch (IOException e) {
            // Ignore errors when clearing cache
        }
    }

    /**
     * Clears all relocation caches.
     */
    public void clearAllCaches() {
        try {
            // This would require walking the entire repository tree
            // For now, we'll leave this as a no-op
            // TODO: Implement if needed
        } catch (Exception e) {
            // Ignore errors when clearing caches
        }
    }

    @Override
    public String toString() {
        return "RelocationCacheResolver{" +
                "localRepository=" + localRepository +
                '}';
    }
}
