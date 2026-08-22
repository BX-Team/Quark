package org.bxteam.quark.pom.model;

import org.bxteam.quark.dependency.Dependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Information extracted from a POM file.
 *
 * @param groupId              the group ID, or null if not specified
 * @param artifactId           the artifact ID
 * @param version              the version, or null if not specified
 * @param dependencies         the list of dependencies
 * @param properties           the map of properties
 * @param dependencyManagement the map of dependency management entries
 * @param parentInfo           the parent POM information, or null if not present
 */
public record PomInfo(@Nullable String groupId,
                      @NotNull String artifactId,
                      @Nullable String version,
                      @NotNull List<Dependency> dependencies,
                      @NotNull Map<String, String> properties,
                      @NotNull Map<String, String> dependencyManagement,
                      @Nullable ParentInfo parentInfo) {
    /**
     * Creates a new PomInfo.
     *
     * @throws NullPointerException if artifactId is null
     */
    public PomInfo {
        requireNonNull(artifactId, "Artifact ID cannot be null");
        dependencies = List.copyOf(dependencies);
        properties = Map.copyOf(properties);
        dependencyManagement = Map.copyOf(dependencyManagement);
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
