package org.bxteam.quark.pom.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Contains Maven metadata information extracted from a maven-metadata.xml file.
 *
 * <p>This record represents the structured information found in Maven repository
 * metadata files, including artifact coordinates and version information.</p>
 *
 * @param groupId the group ID of the artifact, or null if not specified
 * @param artifactId the artifact ID, or null if not specified
 * @param latest the latest version string, or null if not specified
 * @param release the release version string, or null if not specified
 * @param versions the list of available versions, must not be null
 */
public record MavenMetadata(@Nullable String groupId,
                            @Nullable String artifactId,
                            @Nullable String latest,
                            @Nullable String release,
                            @NotNull List<String> versions) {
    /**
     * Constructs a new MavenMetadata instance.
     *
     * @throws NullPointerException if versions is null
     */
    public MavenMetadata {
        versions = List.copyOf(versions);
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
}
