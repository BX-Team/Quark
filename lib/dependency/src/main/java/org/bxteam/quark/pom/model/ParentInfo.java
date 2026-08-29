package org.bxteam.quark.pom.model;

import org.bxteam.quark.dependency.Dependency;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * Parent POM information.
 *
 * @param groupId    the parent group ID
 * @param artifactId the parent artifact ID
 * @param version    the parent version
 */
public record ParentInfo(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
    /**
     * Creates a new ParentInfo.
     *
     * @throws NullPointerException if any parameter is null
     */
    public ParentInfo {
        requireNonNull(groupId, "Group ID cannot be null");
        requireNonNull(artifactId, "Artifact ID cannot be null");
        requireNonNull(version, "Version cannot be null");
    }

    /**
     * Creates a Dependency for this parent.
     *
     * @return a Dependency object representing this parent
     */
    @NotNull
    public Dependency toDependency() {
        return Dependency.of(groupId, artifactId, version);
    }
}
