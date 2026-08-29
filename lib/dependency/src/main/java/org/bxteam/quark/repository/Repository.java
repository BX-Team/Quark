package org.bxteam.quark.repository;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * Represents a Maven repository for dependency resolution.
 *
 * <p>A repository is identified by its base URL and provides access to Maven artifacts
 * through the standard Maven directory structure. This class handles URL normalization
 * and provides a consistent interface for repository access.</p>
 */
public class Repository {
    /**
     * The base URL of the repository, normalized to not end with a slash.
     */
    protected final String baseUrl;

    /**
     * Creates a new repository with the specified base URL.
     *
     * @param url the repository base URL
     * @throws NullPointerException if url is null
     */
    protected Repository(@NotNull String url) {
        this.baseUrl = normalizeUrl(requireNonNull(url, "Repository URL cannot be null"));
    }

    /**
     * Gets the base URL of this repository.
     *
     * @return the repository URL
     */
    @NotNull
    public String getUrl() {
        return baseUrl;
    }

    /**
     * Gets the base URL of this repository (alias for getUrl).
     *
     * @return the repository URL
     */
    @NotNull
    public String url() {
        return baseUrl;
    }

    /**
     * Creates a new repository from a URL string.
     *
     * @param url the repository URL
     * @return a new Repository instance
     * @throws NullPointerException if url is null
     */
    @NotNull
    public static Repository of(@NotNull String url) {
        return new Repository(url);
    }

    /**
     * Normalizes a repository URL by ensuring it doesn't end with a trailing slash.
     *
     * @param url the URL to normalize
     * @return the normalized URL
     */
    @NotNull
    private static String normalizeUrl(@NotNull String url) {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Constructs the full URL to a specific artifact path within this repository.
     *
     * @param artifactPath the path to the artifact (e.g., "com/example/artifact/1.0.0/artifact-1.0.0.jar")
     * @return the complete URL to the artifact
     * @throws NullPointerException if artifactPath is null
     */
    @NotNull
    public String getArtifactUrl(@NotNull String artifactPath) {
        requireNonNull(artifactPath, "Artifact path cannot be null");

        String normalizedPath = artifactPath.startsWith("/") ? artifactPath.substring(1) : artifactPath;

        return baseUrl + "/" + normalizedPath;
    }

    /**
     * Checks if this repository represents a local file system repository.
     *
     * @return true if this is a local repository
     */
    public boolean isLocal() {
        return baseUrl.startsWith("file:");
    }

    /**
     * Checks if this repository represents a remote HTTP/HTTPS repository.
     *
     * @return true if this is a remote repository
     */
    public boolean isRemote() {
        return baseUrl.startsWith("http:") || baseUrl.startsWith("https:");
    }

    /**
     * Gets the repository type as a string.
     *
     * @return "local" for file repositories, "remote" for HTTP repositories, "unknown" for others
     */
    @NotNull
    public String getType() {
        if (isLocal()) {
            return "local";
        } else if (isRemote()) {
            return "remote";
        } else {
            return "unknown";
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Repository that = (Repository) obj;
        return baseUrl.equals(that.baseUrl);
    }

    @Override
    public int hashCode() {
        return baseUrl.hashCode();
    }

    @Override
    public String toString() {
        return baseUrl;
    }
}
