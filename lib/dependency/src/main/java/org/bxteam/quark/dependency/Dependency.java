package org.bxteam.quark.dependency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Represents a Maven dependency with complete coordinate information.
 *
 * <p>This class encapsulates Maven dependency coordinates including group ID, artifact ID,
 * version, and optional classifier. It provides convenient methods for working with
 * Maven repository paths, URLs, and file operations.</p>
 */
public final class Dependency {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String classifier;
    private final String fallbackRepository;
    private final boolean optional;
    private final String scope;

    private Dependency(Builder builder) {
        this.groupId = requireNonNull(builder.groupId, "Group ID cannot be null").trim();
        this.artifactId = requireNonNull(builder.artifactId, "Artifact ID cannot be null").trim();
        this.version = requireNonNull(builder.version, "Version cannot be null").trim();
        this.classifier = builder.classifier != null ? builder.classifier.trim() : null;
        this.fallbackRepository = builder.fallbackRepository;
        this.optional = builder.optional;
        this.scope = builder.scope != null ? builder.scope.trim() : "compile";

        if (this.groupId.isEmpty()) {
            throw new IllegalArgumentException("Group ID cannot be empty");
        }
        if (this.artifactId.isEmpty()) {
            throw new IllegalArgumentException("Artifact ID cannot be empty");
        }
        if (this.version.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be empty");
        }
    }

    /**
     * Creates a new dependency builder instance.
     *
     * @return a new builder for constructing dependencies
     */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a dependency with basic Maven coordinates.
     *
     * @param groupId the Maven group ID
     * @param artifactId the Maven artifact ID
     * @param version the dependency version
     * @return a new dependency instance
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if any parameter is empty
     */
    @NotNull
    public static Dependency of(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
        return builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .build();
    }

    /**
     * Creates a dependency from Maven coordinates string.
     *
     * <p>Supports the standard Maven coordinate format:
     * {@code groupId:artifactId:version[:classifier]}</p>
     *
     * @param coordinates the Maven coordinates string
     * @return a new dependency instance
     * @throws NullPointerException if coordinates is null
     * @throws IllegalArgumentException if coordinates format is invalid
     */
    @NotNull
    public static Dependency fromCoordinates(@NotNull String coordinates) {
        requireNonNull(coordinates, "Coordinates cannot be null");

        String[] parts = coordinates.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid coordinates format. Expected: groupId:artifactId:version[:classifier]");
        }

        Builder builder = builder()
                .groupId(parts[0])
                .artifactId(parts[1])
                .version(parts[2]);

        if (parts.length > 3 && !parts[3].trim().isEmpty()) {
            builder.classifier(parts[3]);
        }

        return builder.build();
    }

    /**
     * Gets the Maven group ID.
     *
     * @return the group ID, never null
     */
    @NotNull
    public String getGroupId() {
        return groupId;
    }

    /**
     * Gets the Maven artifact ID.
     *
     * @return the artifact ID, never null
     */
    @NotNull
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Gets the dependency version.
     *
     * @return the version, never null
     */
    @NotNull
    public String getVersion() {
        return version;
    }

    /**
     * Gets the optional classifier.
     *
     * @return the classifier, or null if not set
     */
    @Nullable
    public String getClassifier() {
        return classifier;
    }

    /**
     * Gets the fallback repository URL.
     *
     * @return the fallback repository URL, or null if not set
     */
    @Nullable
    public String getFallbackRepository() {
        return fallbackRepository;
    }

    /**
     * Checks if this dependency is marked as optional.
     *
     * @return true if the dependency is optional, false otherwise
     */
    public boolean isOptional() {
        return optional;
    }

    /**
     * Gets the dependency scope (e.g., "compile", "test", "runtime").
     *
     * @return the scope, defaults to "compile" if not specified
     */
    @NotNull
    public String getScope() {
        return scope;
    }

    /**
     * Gets the combined group and artifact ID.
     *
     * @return the group and artifact ID in format "groupId:artifactId"
     */
    @NotNull
    public String getGroupArtifactId() {
        return groupId + ":" + artifactId;
    }

    /**
     * Gets the complete Maven coordinates.
     *
     * @return coordinates in format "groupId:artifactId:version[:classifier]"
     */
    @NotNull
    public String getCoordinates() {
        StringBuilder coords = new StringBuilder()
                .append(groupId).append(":")
                .append(artifactId).append(":")
                .append(version);

        if (classifier != null && !classifier.isEmpty()) {
            coords.append(":").append(classifier);
        }

        return coords.toString();
    }

    /**
     * Gets the relative path for this dependency in a Maven repository.
     *
     * @return the repository path (e.g., "org/example/my-lib/1.0.0")
     */
    @NotNull
    public String getRepositoryPath() {
        String groupPath = groupId.replace('.', '/');
        return groupPath + "/" + artifactId + "/" + version;
    }

    /**
     * Gets the JAR filename for this dependency.
     *
     * @return the JAR filename (e.g., "my-lib-1.0.0.jar" or "my-lib-1.0.0-sources.jar")
     */
    @NotNull
    public String getJarFileName() {
        StringBuilder fileName = new StringBuilder()
                .append(artifactId).append("-").append(version);

        if (classifier != null && !classifier.isEmpty()) {
            fileName.append("-").append(classifier);
        }

        fileName.append(".jar");
        return fileName.toString();
    }

    /**
     * Gets the POM filename for this dependency.
     *
     * @return the POM filename (e.g., "my-lib-1.0.0.pom")
     */
    @NotNull
    public String getPomFileName() {
        return artifactId + "-" + version + ".pom";
    }

    /**
     * Gets the absolute JAR file path within a repository.
     *
     * @param repositoryRoot the repository root directory
     * @return the complete path to the JAR file
     */
    @NotNull
    public Path getJarPath(@NotNull Path repositoryRoot) {
        return repositoryRoot.resolve(getRepositoryPath()).resolve(getJarFileName());
    }

    /**
     * Gets the absolute POM file path within a repository.
     *
     * @param repositoryRoot the repository root directory
     * @return the complete path to the POM file
     */
    @NotNull
    public Path getPomPath(@NotNull Path repositoryRoot) {
        return repositoryRoot.resolve(getRepositoryPath()).resolve(getPomFileName());
    }

    /**
     * Gets the JAR download URI for a given repository.
     *
     * @param repositoryUrl the base repository URL
     * @return the complete URI to download the JAR
     * @throws URISyntaxException if the resulting URI is malformed
     */
    @NotNull
    public URI getJarUri(@NotNull String repositoryUrl) throws URISyntaxException {
        String cleanUrl = repositoryUrl.endsWith("/") ? repositoryUrl : repositoryUrl + "/";
        String jarPath = getRepositoryPath() + "/" + getJarFileName();
        return new URI(cleanUrl + jarPath);
    }

    /**
     * Gets the POM download URI for a given repository.
     *
     * @param repositoryUrl the base repository URL
     * @return the complete URI to download the POM
     * @throws URISyntaxException if the resulting URI is malformed
     */
    @NotNull
    public URI getPomUri(@NotNull String repositoryUrl) throws URISyntaxException {
        String cleanUrl = repositoryUrl.endsWith("/") ? repositoryUrl : repositoryUrl + "/";
        String pomPath = getRepositoryPath() + "/" + getPomFileName();
        return new URI(cleanUrl + pomPath);
    }

    /**
     * Gets the maven-metadata.xml URI for a given repository.
     *
     * @param repositoryUrl the base repository URL
     * @return the URI to the metadata file
     * @throws URISyntaxException if the resulting URI is malformed
     */
    @NotNull
    public URI getMetadataUri(@NotNull String repositoryUrl) throws URISyntaxException {
        String cleanUrl = repositoryUrl.endsWith("/") ? repositoryUrl : repositoryUrl + "/";
        String groupPath = groupId.replace('.', '/');
        String metadataPath = groupPath + "/" + artifactId + "/maven-metadata.xml";
        return new URI(cleanUrl + metadataPath);
    }

    /**
     * Creates a new dependency with a different version.
     *
     * @param newVersion the new version
     * @return a new dependency instance with the updated version
     */
    @NotNull
    public Dependency withVersion(@NotNull String newVersion) {
        return builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(newVersion)
                .classifier(classifier)
                .fallbackRepository(fallbackRepository)
                .optional(optional)
                .scope(scope)
                .build();
    }

    /**
     * Creates a new dependency with a different classifier.
     *
     * @param newClassifier the new classifier, or null to remove
     * @return a new dependency instance with the updated classifier
     */
    @NotNull
    public Dependency withClassifier(@Nullable String newClassifier) {
        return builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .classifier(newClassifier)
                .fallbackRepository(fallbackRepository)
                .optional(optional)
                .scope(scope)
                .build();
    }

    /**
     * Creates a new dependency with a different fallback repository.
     *
     * @param newFallbackRepository the new fallback repository URL, or null to remove
     * @return a new dependency instance with the updated fallback repository
     */
    @NotNull
    public Dependency withFallbackRepository(@Nullable String newFallbackRepository) {
        return builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .classifier(classifier)
                .fallbackRepository(newFallbackRepository)
                .optional(optional)
                .scope(scope)
                .build();
    }

    /**
     * Creates a new dependency with a different optional flag.
     *
     * @param newOptional whether the dependency is optional
     * @return a new dependency instance with the updated optional flag
     */
    @NotNull
    public Dependency withOptional(boolean newOptional) {
        return builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .classifier(classifier)
                .fallbackRepository(fallbackRepository)
                .optional(newOptional)
                .scope(scope)
                .build();
    }

    /**
     * Creates a new dependency with a different scope.
     *
     * @param newScope the new scope
     * @return a new dependency instance with the updated scope
     */
    @NotNull
    public Dependency withScope(@Nullable String newScope) {
        return builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .classifier(classifier)
                .fallbackRepository(fallbackRepository)
                .optional(optional)
                .scope(newScope)
                .build();
    }

    /**
     * Checks if this dependency represents the same artifact as another.
     *
     * <p>This comparison ignores version, classifier, and fallback repository,
     * only comparing group and artifact IDs.</p>
     *
     * @param other the other dependency to compare
     * @return true if they represent the same artifact
     */
    public boolean isSameArtifact(@NotNull Dependency other) {
        return Objects.equals(this.groupId, other.groupId) &&
                Objects.equals(this.artifactId, other.artifactId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Dependency that = (Dependency) obj;
        return Objects.equals(groupId, that.groupId) &&
                Objects.equals(artifactId, that.artifactId) &&
                Objects.equals(version, that.version) &&
                Objects.equals(classifier, that.classifier) &&
                optional == that.optional &&
                Objects.equals(scope, that.scope);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version, classifier, optional, scope);
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder(getCoordinates());

        if (optional) {
            result.append(" (optional)");
        }

        if (!"compile".equals(scope)) {
            result.append(" (").append(scope).append(")");
        }

        if (fallbackRepository != null) {
            result.append(" (fallback: ").append(fallbackRepository).append(")");
        }

        return result.toString();
    }

    /**
     * Creates a compact string representation.
     *
     * @return coordinates without fallback repository information
     */
    @NotNull
    public String toShortString() {
        return groupId + ":" + artifactId + ":" + version;
    }

    /**
     * Builder for creating {@link Dependency} instances.
     *
     * <p>Provides a fluent API for constructing dependencies with optional parameters.</p>
     */
    public static final class Builder {
        private String groupId;
        private String artifactId;
        private String version;
        private String classifier;
        private String fallbackRepository;
        private boolean optional;
        private String scope;

        private Builder() {}

        /**
         * Sets the Maven group ID.
         *
         * @param groupId the group ID
         * @return this builder for method chaining
         * @throws NullPointerException if groupId is null
         */
        @NotNull
        public Builder groupId(@NotNull String groupId) {
            this.groupId = requireNonNull(groupId, "Group ID cannot be null");
            return this;
        }

        /**
         * Sets the Maven artifact ID.
         *
         * @param artifactId the artifact ID
         * @return this builder for method chaining
         * @throws NullPointerException if artifactId is null
         */
        @NotNull
        public Builder artifactId(@NotNull String artifactId) {
            this.artifactId = requireNonNull(artifactId, "Artifact ID cannot be null");
            return this;
        }

        /**
         * Sets the dependency version.
         *
         * @param version the version
         * @return this builder for method chaining
         * @throws NullPointerException if version is null
         */
        @NotNull
        public Builder version(@NotNull String version) {
            this.version = requireNonNull(version, "Version cannot be null");
            return this;
        }

        /**
         * Sets the optional classifier.
         *
         * @param classifier the classifier, or null for no classifier
         * @return this builder for method chaining
         */
        @NotNull
        public Builder classifier(@Nullable String classifier) {
            this.classifier = classifier;
            return this;
        }

        /**
         * Sets the fallback repository URL.
         *
         * @param fallbackRepository the fallback repository URL, or null for no fallback
         * @return this builder for method chaining
         */
        @NotNull
        public Builder fallbackRepository(@Nullable String fallbackRepository) {
            this.fallbackRepository = fallbackRepository;
            return this;
        }

        /**
         * Sets whether the dependency is optional.
         *
         * @param optional true if the dependency is optional, false otherwise
         * @return this builder for method chaining
         */
        @NotNull
        public Builder optional(boolean optional) {
            this.optional = optional;
            return this;
        }

        /**
         * Sets the dependency scope.
         *
         * @param scope the scope (e.g., "compile", "test", "runtime")
         * @return this builder for method chaining
         */
        @NotNull
        public Builder scope(@Nullable String scope) {
            this.scope = scope;
            return this;
        }

        /**
         * Builds the dependency instance.
         *
         * @return a new dependency with the configured properties
         * @throws NullPointerException if any required field is null
         * @throws IllegalArgumentException if any required field is empty
         */
        @NotNull
        public Dependency build() {
            return new Dependency(this);
        }
    }
}
