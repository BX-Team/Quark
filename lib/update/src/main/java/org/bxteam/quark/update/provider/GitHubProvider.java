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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static java.util.Objects.requireNonNull;

/**
 * Latest <a href="https://docs.github.com/en/rest/releases/releases#get-the-latest-release">GitHub release</a>
 * of a repository. Drafts and pre-releases are excluded by GitHub; the tag name is the version
 * (a leading {@code v} is ignored).
 */
public final class GitHubProvider implements UpdateProvider {
    private static final URI API = URI.create("https://api.github.com/");

    private final String owner;
    private final String repository;
    private final Http http;
    private final URI api;

    /**
     * @param owner the repository owner
     * @param repository the repository name
     */
    public GitHubProvider(@NotNull String owner, @NotNull String repository) {
        this(owner, repository, null);
    }

    /**
     * @param owner the repository owner
     * @param repository the repository name
     * @param executor the executor for HTTP work, or null for the shared update thread
     */
    public GitHubProvider(@NotNull String owner, @NotNull String repository, @Nullable Executor executor) {
        this(owner, repository, new Http(executor), API);
    }

    GitHubProvider(String owner, String repository, Http http, URI api) {
        this.owner = requireNonNull(owner, "Owner cannot be null");
        this.repository = requireNonNull(repository, "Repository cannot be null");
        this.http = http;
        this.api = api;
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<ReleaseInfo>> latest() {
        URI uri = api.resolve("repos/" + Releases.encode(owner) + "/" + Releases.encode(repository) + "/releases/latest");
        // 404 means the repository has no published release yet
        return http.get(uri, "application/vnd.github+json").thenApply(body -> body.map(text -> {
            if (!(Releases.parse(text, uri) instanceof Map<?, ?> release)) {
                throw new UpdateCheckException("Expected a JSON object from " + uri);
            }
            String tag = Json.string(release.get("tag_name"));
            if (tag == null || tag.isBlank()) {
                throw new UpdateCheckException("GitHub release without tag_name in " + owner + "/" + repository);
            }
            return new ReleaseInfo(
                    SemanticVersion.parse(tag),
                    Json.string(release.get("name")),
                    Releases.uri(Json.string(release.get("html_url"))),
                    Releases.instant(release.get("published_at")));
        }));
    }
}
