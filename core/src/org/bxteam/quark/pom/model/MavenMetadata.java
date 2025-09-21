package org.bxteam.quark.pom.model;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Contains Maven metadata information extracted from a maven-metadata.xml file.
 *
 * <p>This record represents the structured information found in Maven repository
 * metadata files, including artifact coordinates and version information.</p>
 */
@Getter
public final class MavenMetadata {
    private final @Nullable String groupId;
    private final @Nullable String artifactId;
    private final @Nullable String latest;
    private final @Nullable String release;
    private final @NotNull List<String> versions;

    public MavenMetadata(@Nullable String groupId, @Nullable String artifactId, @Nullable String latest, @Nullable String release, @NotNull List<String> versions) {
        versions = List.copyOf(versions);
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.latest = latest;
        this.release = release;
        this.versions = versions;
    }

    /**
     * Gets the best version to use based on available version information.
     *
     * <p>The selection priority is:</p>
     * <ol>
     *   <li>Release version (if available and non-empty)</li>
     *   <li>Latest version (if available and non-empty)</li>
     *   <li>Last version in the versions list (if list is not empty)</li>
     *   <li>null if no version information is available</li>
     * </ol>
     *
     * @return the best available version, or null if no version information is available
     */
    @Nullable
    public String getBestVersion() {
        if (release != null && !release.trim().isEmpty()) {
            return release;
        }
        if (latest != null && !latest.trim().isEmpty()) {
            return latest;
        }
        if (!versions.isEmpty()) {
            return versions.get(versions.size() - 1);
        }
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (MavenMetadata) obj;
        return Objects.equals(this.groupId, that.groupId) &&
            Objects.equals(this.artifactId, that.artifactId) &&
            Objects.equals(this.latest, that.latest) &&
            Objects.equals(this.release, that.release) &&
            Objects.equals(this.versions, that.versions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, latest, release, versions);
    }

    @Override
    public String toString() {
        return "MavenMetadata[" +
            "groupId=" + groupId + ", " +
            "artifactId=" + artifactId + ", " +
            "latest=" + latest + ", " +
            "release=" + release + ", " +
            "versions=" + versions + ']';
    }
}
