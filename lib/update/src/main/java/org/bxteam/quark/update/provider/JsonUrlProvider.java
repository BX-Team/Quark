package org.bxteam.quark.update.provider;

import org.bxteam.quark.common.SemanticVersion;
import org.bxteam.quark.update.ReleaseInfo;
import org.bxteam.quark.update.UpdateCheckException;
import org.bxteam.quark.update.UpdateProvider;
import org.bxteam.quark.update.internal.Http;
import org.bxteam.quark.update.internal.Json;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

/**
 * Reads the version from any endpoint that returns JSON.
 *
 * <p>The path uses dots for object keys and brackets for array indexes: {@code "version"},
 * {@code "data.latest.name"}, {@code "[0].tag"}. A missing value means no release.</p>
 */
public final class JsonUrlProvider implements UpdateProvider {
    private final URI url;
    private final String versionPath;
    private final URI downloadPage;
    private final Http http;

    /**
     * @param url the endpoint
     * @param versionPath the path of the version in the response
     */
    public JsonUrlProvider(@NotNull URI url, @NotNull String versionPath) {
        this(url, versionPath, null, (Executor) null);
    }

    /**
     * @param url the endpoint
     * @param versionPath the path of the version in the response
     * @param downloadPage the page reported as {@link ReleaseInfo#url()}, or null
     * @param executor the executor for HTTP work, or null for the shared update thread
     */
    public JsonUrlProvider(@NotNull URI url, @NotNull String versionPath, @Nullable URI downloadPage, @Nullable Executor executor) {
        this(url, versionPath, downloadPage, new Http(executor));
    }

    JsonUrlProvider(URI url, String versionPath, URI downloadPage, Http http) {
        this.url = requireNonNull(url, "URL cannot be null");
        this.versionPath = requireNonNull(versionPath, "Version path cannot be null");
        this.downloadPage = downloadPage;
        this.http = http;
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<ReleaseInfo>> latest() {
        return http.get(url, Releases.JSON).thenApply(body -> body.flatMap(text -> {
            Object value = Json.path(Releases.parse(text, url), versionPath);
            if (value != null && Json.string(value) == null) {
                throw new UpdateCheckException("Value at '" + versionPath + "' in " + url + " is not a version: " + value);
            }
            return Optional.ofNullable(Json.string(value))
                    .filter(version -> !version.isBlank())
                    .map(version -> new ReleaseInfo(SemanticVersion.parse(version), null, downloadPage, null));
        }));
    }
}
