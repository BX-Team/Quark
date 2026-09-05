package org.bxteam.quark.update.provider;

import org.bxteam.quark.common.SemanticVersion;
import org.bxteam.quark.update.ReleaseInfo;
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
 * Latest release of a <a href="https://hangar.papermc.io">Hangar</a> project.
 */
public final class HangarProvider implements UpdateProvider {
    private static final URI API = URI.create("https://hangar.papermc.io/");

    private final String slug;
    private final Http http;
    private final URI api;

    /**
     * @param slug the project slug
     */
    public HangarProvider(@NotNull String slug) {
        this(slug, null);
    }

    /**
     * @param slug the project slug
     * @param executor the executor for HTTP work, or null for the shared update thread
     */
    public HangarProvider(@NotNull String slug, @Nullable Executor executor) {
        this(slug, new Http(executor), API);
    }

    HangarProvider(String slug, Http http, URI api) {
        this.slug = requireNonNull(slug, "Slug cannot be null");
        this.http = http;
        this.api = api;
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<ReleaseInfo>> latest() {
        String project = "api/v1/projects/" + Releases.encode(slug);
        URI latestRelease = api.resolve(project + "/latestrelease");

        return http.get(latestRelease, "text/plain").thenCompose(body -> {
            Optional<String> version = body.map(String::trim).filter(text -> !text.isEmpty());
            if (version.isEmpty()) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            // the download page needs the owner, which only the project endpoint knows; the version is enough without it
            URI projectUri = api.resolve(project);
            return http.get(projectUri, Releases.JSON)
                    .thenApply(projectBody -> projectBody
                            .map(text -> Json.string(Json.path(Releases.parse(text, projectUri), "namespace.owner")))
                            .map(owner -> api.resolve(Releases.encode(owner) + "/" + Releases.encode(slug) + "/versions/" + Releases.encode(version.get())))
                            .orElse(null))
                    .exceptionally(error -> null)
                    .thenApply(page -> Optional.of(new ReleaseInfo(SemanticVersion.parse(version.get()), null, page, null)));
        });
    }
}
