package org.bxteam.quark.repository;

import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Represents a local file system Maven repository.
 *
 * <p>This repository type points to a directory on the local file system
 * that contains Maven artifacts in the standard Maven directory structure.
 * It's commonly used for local Maven repositories (~/.m2/repository) or
 * custom local caches.</p>
 */
public class LocalRepository extends Repository {
    private final Path repositoryPath;

    /**
     * Creates a new local repository from a directory path.
     *
     * @param repositoryFolder the path to the repository directory
     * @throws NullPointerException if repositoryFolder is null
     * @throws RepositoryException if the path cannot be converted to a valid URL
     */
    public LocalRepository(@NotNull Path repositoryFolder) {
        super(convertPathToUrl(repositoryFolder));
        this.repositoryPath = requireNonNull(repositoryFolder, "Repository folder cannot be null");
    }

    /**
     * Gets the file system path to this repository.
     *
     * @return the repository path
     */
    @NotNull
    public Path getPath() {
        return repositoryPath;
    }

    /**
     * Checks if the repository directory exists.
     *
     * @return true if the directory exists
     */
    public boolean exists() {
        return Files.exists(repositoryPath);
    }

    /**
     * Checks if the repository directory is readable.
     *
     * @return true if the directory is readable
     */
    public boolean isReadable() {
        return Files.isReadable(repositoryPath);
    }

    /**
     * Checks if the repository directory is a directory.
     *
     * @return true if the path is a directory
     */
    public boolean isDirectory() {
        return Files.isDirectory(repositoryPath);
    }

    /**
     * Creates the repository directory if it doesn't exist.
     *
     * @throws RepositoryException if directory creation fails
     */
    public void ensureExists() {
        if (!exists()) {
            try {
                Files.createDirectories(repositoryPath);
            } catch (Exception e) {
                throw new RepositoryException("Failed to create repository directory: " + repositoryPath, e);
            }
        }
    }

    /**
     * Resolves a relative path within this repository.
     *
     * @param relativePath the relative path
     * @return the resolved absolute path
     * @throws NullPointerException if relativePath is null
     */
    @NotNull
    public Path resolve(@NotNull String relativePath) {
        requireNonNull(relativePath, "Relative path cannot be null");
        return repositoryPath.resolve(relativePath);
    }

    /**
     * Creates a new local repository from a string path.
     *
     * @param repositoryPath the path string
     * @return a new LocalRepository instance
     * @throws NullPointerException if repositoryPath is null
     */
    @NotNull
    public static LocalRepository of(@NotNull String repositoryPath) {
        requireNonNull(repositoryPath, "Repository path cannot be null");
        return new LocalRepository(Path.of(repositoryPath));
    }

    /**
     * Creates a local repository for the user's Maven local repository.
     *
     * @return a LocalRepository pointing to ~/.m2/repository
     */
    @NotNull
    public static LocalRepository mavenLocal() {
        String userHome = System.getProperty("user.home");
        Path mavenLocalPath = Path.of(userHome, ".m2", "repository");
        return new LocalRepository(mavenLocalPath);
    }

    /**
     * Converts a file system path to a file:// URL string.
     */
    @NotNull
    private static String convertPathToUrl(@NotNull Path path) {
        requireNonNull(path, "Path cannot be null");

        try {
            return path.toUri().toURL().toString();
        } catch (MalformedURLException e) {
            throw new RepositoryException("Failed to convert path to URL: " + path, e);
        }
    }

    @Override
    public String toString() {
        return "LocalRepository{path=" + repositoryPath + ", url=" + baseUrl + "}";
    }

    /**
     * Exception thrown when repository operations fail.
     */
    public static class RepositoryException extends RuntimeException {
        public RepositoryException(String message) {
            super(message);
        }

        public RepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
