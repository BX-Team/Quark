package org.bxteam.quark.update;

import org.bxteam.quark.common.SemanticVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.time.Instant;

import static java.util.Objects.requireNonNull;

/**
 * A published release of a plugin.
 *
 * @param version the release version
 * @param name the release title, or null if the source has none
 * @param url the page to download the release from, or null if unknown
 * @param publishedAt when the release was published, or null if unknown
 */
public record ReleaseInfo(@NotNull SemanticVersion version,
                          @Nullable String name,
                          @Nullable URI url,
                          @Nullable Instant publishedAt) {
    /**
     * @throws NullPointerException if version is null
     */
    public ReleaseInfo {
        requireNonNull(version, "Version cannot be null");
    }
}
