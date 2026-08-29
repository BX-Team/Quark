package org.bxteam.quark.dependency.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Result of a JAR download operation.
 *
 * @param jarPath        the path to the downloaded JAR file
 * @param downloadedFrom the repository URL the JAR was downloaded from, or null if cached
 */
public record DownloadResult(@NotNull Path jarPath, @Nullable String downloadedFrom) {
    /**
     * Creates a new download result.
     *
     * @throws NullPointerException if jarPath is null
     */
    public DownloadResult {
        requireNonNull(jarPath, "JAR path cannot be null");
    }
}
