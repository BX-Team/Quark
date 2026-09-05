package org.bxteam.quark.update;

import org.bxteam.quark.update.provider.FirstOfProvider;
import org.bxteam.quark.update.provider.GitHubProvider;
import org.bxteam.quark.update.provider.HangarProvider;
import org.bxteam.quark.update.provider.JsonUrlProvider;
import org.bxteam.quark.update.provider.ModrinthProvider;
import org.bxteam.quark.update.provider.ReposiliteProvider;
import org.bxteam.quark.update.provider.SpigotProvider;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A source of release information.
 *
 * <p>The factories below use a shared daemon thread for HTTP; every provider class also has a constructor that
 * takes an {@link java.util.concurrent.Executor}, e.g. {@code platform.scheduler().asExecutor()}.</p>
 */
public interface UpdateProvider {
    /**
     * Fetches the newest release.
     *
     * @return the newest release, or empty if none is published; completes exceptionally if the source fails
     */
    @NotNull
    CompletableFuture<Optional<ReleaseInfo>> latest();

    /**
     * @param project the Modrinth project id or slug
     * @return a provider for the newest Modrinth release
     */
    @NotNull
    static UpdateProvider modrinth(@NotNull String project) {
        return new ModrinthProvider(project);
    }

    /**
     * @param project the Modrinth project id or slug
     * @param loaders only versions for one of these loaders count, e.g. {@code "paper", "spigot"}
     * @return a provider for the newest Modrinth release for the given loaders
     */
    @NotNull
    static UpdateProvider modrinth(@NotNull String project, @NotNull String... loaders) {
        return new ModrinthProvider(project, false, List.of(loaders), null);
    }

    /**
     * @param slug the Hangar project slug
     * @return a provider for the newest Hangar release
     */
    @NotNull
    static UpdateProvider hangar(@NotNull String slug) {
        return new HangarProvider(slug);
    }

    /**
     * @param owner the repository owner
     * @param repository the repository name
     * @return a provider for the latest GitHub release (drafts and pre-releases excluded)
     */
    @NotNull
    static UpdateProvider github(@NotNull String owner, @NotNull String repository) {
        return new GitHubProvider(owner, repository);
    }

    /**
     * @param resourceId the SpigotMC resource id
     * @return a provider for the current SpigotMC version
     */
    @NotNull
    static UpdateProvider spigot(long resourceId) {
        return new SpigotProvider(resourceId);
    }

    /**
     * @param host the Reposilite host, e.g. {@code https://repo.bxteam.org}
     * @param repository the repository name, e.g. {@code releases}
     * @param groupId the artifact group
     * @param artifactId the artifact id
     * @return a provider for the latest version in a Reposilite Maven repository
     */
    @NotNull
    static UpdateProvider reposilite(@NotNull URI host, @NotNull String repository, @NotNull String groupId, @NotNull String artifactId) {
        return new ReposiliteProvider(host, repository, groupId, artifactId);
    }

    /**
     * @param url an endpoint returning JSON
     * @param versionPath the path of the version in the JSON, e.g. {@code "version"} or {@code "releases[0].tag"}
     * @return a provider reading the version from a custom endpoint
     */
    @NotNull
    static UpdateProvider json(@NotNull URI url, @NotNull String versionPath) {
        return new JsonUrlProvider(url, versionPath);
    }

    /**
     * Asks the providers in order and returns the first release found; a failing or empty provider is skipped.
     *
     * @param providers the providers, in order of preference
     * @return the combined provider
     */
    @NotNull
    static UpdateProvider firstOf(@NotNull UpdateProvider... providers) {
        return new FirstOfProvider(List.of(providers));
    }
}
