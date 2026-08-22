package org.bxteam.quark.pom.model;

import lombok.Getter;
import org.bxteam.quark.dependency.Dependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Information extracted from a POM file.
 */
@Getter
public final class PomInfo {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final List<Dependency> dependencies;
    private final Map<String, String> properties;
    private final Map<String, String> dependencyManagement;
    private final ParentInfo parentInfo;

    /**
     * Creates a new PomInfo.
     *
     * @param groupId              the group ID, or null if not specified
     * @param artifactId           the artifact ID
     * @param version              the version, or null if not specified
     * @param dependencies         the list of dependencies
     * @param properties           the map of properties
     * @param dependencyManagement the map of dependency management entries
     * @param parentInfo           the parent POM information, or null if not present
     * @throws NullPointerException if artifactId is null
     */
    public PomInfo(@Nullable String groupId,
                   @NotNull String artifactId,
                   @Nullable String version,
                   @NotNull List<Dependency> dependencies,
                   @NotNull Map<String, String> properties,
                   @NotNull Map<String, String> dependencyManagement,
                   @Nullable ParentInfo parentInfo) {
        this.groupId = groupId;
        this.artifactId = requireNonNull(artifactId, "Artifact ID cannot be null");
        this.version = version;
        this.dependencies = List.copyOf(dependencies);
        this.properties = Map.copyOf(properties);
        this.dependencyManagement = Map.copyOf(dependencyManagement);
        this.parentInfo = parentInfo;
    }

    /**
     * Gets runtime dependencies (filtering out test and provided scope).
     *
     * @return the list of runtime dependencies
     */
    @NotNull
    public List<Dependency> getRuntimeDependencies() {
        return dependencies;
    }

    /**
     * Checks if this POM has a parent.
     *
     * @return true if this POM has a parent, false otherwise
     */
    public boolean hasParent() {
        return parentInfo != null;
    }

    /**
     * Gets the project dependency if groupId and version are available.
     *
     * @return the project dependency, or null if insufficient information
     */
    @Nullable
    public Dependency getProjectDependency() {
        if (groupId != null && version != null) {
            return Dependency.of(groupId, artifactId, version);
        }
        return null;
    }
}
