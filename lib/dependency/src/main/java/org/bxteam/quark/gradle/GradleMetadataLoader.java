package org.bxteam.quark.gradle;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.manifest.DependencyManifest;
import org.bxteam.quark.manifest.ManifestException;
import org.bxteam.quark.relocation.Relocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Loads dependencies and configuration from Gradle plugin generated metadata.
 *
 * @deprecated Quark 2.0 replaced the {@code quark/*.txt} files with a single manifest.
 *             Use {@link DependencyManifest#load(org.bxteam.quark.ResourceProvider)} instead.
 */
@Deprecated(since = "2.0.0", forRemoval = true)
public class GradleMetadataLoader {
    private final DependencyManifest manifest;

    /**
     * Creates a new metadata loader and loads the manifest from resources.
     *
     * @param resourceProvider the provider for accessing embedded resources
     * @throws GradleMetadataException if the manifest is missing or cannot be parsed
     */
    public GradleMetadataLoader(@NotNull ResourceProvider resourceProvider) {
        requireNonNull(resourceProvider, "Resource provider cannot be null");

        try {
            this.manifest = DependencyManifest.load(resourceProvider)
                    .orElseThrow(() -> new GradleMetadataException("No " + DependencyManifest.LOCATION + " found"));
        } catch (ManifestException e) {
            throw new GradleMetadataException(e.getMessage(), e);
        }
    }

    /**
     * @return immutable list of dependencies
     */
    @NotNull
    public List<Dependency> getDependencies() {
        return manifest.dependencies();
    }

    /**
     * @return immutable list of repository URLs
     */
    @NotNull
    public List<String> getRepositories() {
        return manifest.repositories();
    }

    /**
     * @return immutable list of relocations
     */
    @NotNull
    public List<Relocation> getRelocations() {
        return manifest.relocations();
    }

    /**
     * @return true if relocations are available
     */
    public boolean hasRelocations() {
        return !manifest.relocations().isEmpty();
    }

    /**
     * @return true if dependencies are available
     */
    public boolean hasDependencies() {
        return !manifest.dependencies().isEmpty();
    }

    /**
     * @return true if repositories are available
     */
    public boolean hasRepositories() {
        return !manifest.repositories().isEmpty();
    }

    /**
     * Interface for providing access to embedded resources.
     *
     * @deprecated Use {@link org.bxteam.quark.ResourceProvider} instead.
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    @FunctionalInterface
    public interface ResourceProvider extends org.bxteam.quark.ResourceProvider {
    }

    /**
     * Exception thrown when Gradle metadata loading fails.
     *
     * @deprecated Use {@link ManifestException} instead.
     */
    @Deprecated(since = "2.0.0", forRemoval = true)
    public static class GradleMetadataException extends RuntimeException {
        /**
         * @param message the detail message
         */
        public GradleMetadataException(String message) {
            super(message);
        }

        /**
         * @param message the detail message
         * @param cause the cause of this exception
         */
        public GradleMetadataException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
