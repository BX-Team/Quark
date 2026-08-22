package org.bxteam.quark.pom.model;

import lombok.Getter;
import org.bxteam.quark.dependency.Dependency;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * Parent POM information.
 */
@Getter
public final class ParentInfo {
    private final @NotNull String groupId;
    private final @NotNull String artifactId;
    private final @NotNull String version;

    /**
     * Creates a new ParentInfo.
     *
     * @param groupId    the parent group ID
     * @param artifactId the parent artifact ID
     * @param version    the parent version
     * @throws NullPointerException if any parameter is null
     */
    public ParentInfo(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
        requireNonNull(groupId, "Group ID cannot be null");
        requireNonNull(artifactId, "Artifact ID cannot be null");
        requireNonNull(version, "Version cannot be null");
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
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
