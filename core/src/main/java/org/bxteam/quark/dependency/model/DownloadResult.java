package org.bxteam.quark.dependency.model;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * Result of a JAR download operation.
 */
@Getter
public final class DownloadResult {
    private final @NotNull Path jarPath;
    private final @Nullable String downloadedFrom;

    /**
     * Creates a new download result.
     *
     * @param jarPath        the path to the downloaded JAR file
     * @param downloadedFrom the repository URL the JAR was downloaded from, or null if cached
     * @throws NullPointerException if jarPath is null
     */
    public DownloadResult(@NotNull Path jarPath, @Nullable String downloadedFrom) {
        requireNonNull(jarPath, "JAR path cannot be null");
        this.jarPath = jarPath;
        this.downloadedFrom = downloadedFrom;
    }
}
