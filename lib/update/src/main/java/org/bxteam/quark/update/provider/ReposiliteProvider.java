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
 * Latest version of an artifact in a <a href="https://reposilite.com">Reposilite</a> Maven repository.
 */
public final class ReposiliteProvider implements UpdateProvider {
    private final URI host;
    private final String repository;
    private final String groupId;
    private final String artifactId;
    private final URI downloadPage;
    private final Http http;

    /**
     * @param host the Reposilite host
     * @param repository the repository name, e.g. {@code releases}
     * @param groupId the artifact group
     * @param artifactId the artifact id
     */
    public ReposiliteProvider(@NotNull URI host, @NotNull String repository, @NotNull String groupId, @NotNull String artifactId) {
        this(host, repository, groupId, artifactId, null, (Executor) null);
    }

    /**
     * @param host the Reposilite host
     * @param repository the repository name, e.g. {@code releases}
     * @param groupId the artifact group
     * @param artifactId the artifact id
     * @param downloadPage the page reported as {@link ReleaseInfo#url()}, or null
     * @param executor the executor for HTTP work, or null for the shared update thread
     */
    public ReposiliteProvider(@NotNull URI host, @NotNull String repository, @NotNull String groupId, @NotNull String artifactId,
                              @Nullable URI downloadPage, @Nullable Executor executor) {
        this(host, repository, groupId, artifactId, downloadPage, new Http(executor));
    }

    ReposiliteProvider(URI host, String repository, String groupId, String artifactId, URI downloadPage, Http http) {
        this.host = requireNonNull(host, "Host cannot be null");
        this.repository = requireNonNull(repository, "Repository cannot be null");
        this.groupId = requireNonNull(groupId, "Group id cannot be null");
        this.artifactId = requireNonNull(artifactId, "Artifact id cannot be null");
        this.downloadPage = downloadPage;
        this.http = http;
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<ReleaseInfo>> latest() {
        String base = host.toString().endsWith("/") ? host.toString() : host + "/";
        URI uri = URI.create(base + "api/maven/latest/version/" + Releases.encode(repository) + "/"
                + groupId.replace('.', '/') + "/" + Releases.encode(artifactId));

        return http.get(uri, Releases.JSON).thenApply(body -> body.map(text -> {
            String version = Json.string(Json.path(Releases.parse(text, uri), "version"));
            if (version == null || version.isBlank()) {
                throw new UpdateCheckException("No version in the response of " + uri);
            }
            return new ReleaseInfo(SemanticVersion.parse(version), null, downloadPage, null);
        }));
    }
}
