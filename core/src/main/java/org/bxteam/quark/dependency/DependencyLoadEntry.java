package org.bxteam.quark.dependency;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Represents a loaded dependency with its file path.
 *
 * <p>This record holds the mapping between a dependency and its physical
 * location on the file system after it has been downloaded and potentially
 * relocated.</p>
 *
 * @param dependency the dependency that was loaded
 * @param path the path to the JAR file on disk
 */
public record DependencyLoadEntry(@NotNull Dependency dependency, @NotNull Path path) {
    /**
     * Creates a new dependency load entry.
     *
     * @param dependency the dependency
     * @param path the path to the JAR file
     * @throws NullPointerException if any parameter is null
     */
    public DependencyLoadEntry(@NotNull Dependency dependency, @NotNull Path path) {
        this.dependency = requireNonNull(dependency, "Dependency cannot be null");
        this.path = requireNonNull(path, "Path cannot be null");
    }

    /**
     * Gets the dependency coordinates as a string.
     *
     * @return the dependency coordinates
     */
    @NotNull
    public String getCoordinates() {
        return dependency.getCoordinates();
    }

    /**
     * Gets the file name of the JAR.
     *
     * @return the file name
     */
    @NotNull
    public String getFileName() {
        return path.getFileName().toString();
    }

    /**
     * Checks if the JAR file exists.
     *
     * @return true if the file exists
     */
    public boolean exists() {
        return java.nio.file.Files.exists(path);
    }

    /**
     * Gets the file size in bytes.
     *
     * @return the file size or -1 if unknown
     */
    public long getFileSize() {
        try {
            return java.nio.file.Files.size(path);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public @NotNull String toString() {
        return "DependencyLoadEntry{" +
                "dependency=" + dependency +
                ", path=" + path.getFileName() +
                ", size=" + getFileSize() + " bytes" +
                '}';
    }
}
