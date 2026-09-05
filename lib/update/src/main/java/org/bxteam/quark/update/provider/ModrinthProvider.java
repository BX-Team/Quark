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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Newest release of a <a href="https://modrinth.com">Modrinth</a> project.
 */
public final class ModrinthProvider implements UpdateProvider {
    private static final URI API = URI.create("https://api.modrinth.com/");

    private final String project;
    private final boolean includePreReleases;
    private final List<String> loaders;
    private final Http http;
    private final URI api;

    /**
     * @param project the project id or slug
     */
    public ModrinthProvider(@NotNull String project) {
        this(project, false, List.of(), null);
    }

    /**
     * @param project the project id or slug
     * @param includePreReleases whether beta and alpha versions count as releases
     * @param loaders only versions for one of these loaders count (e.g. {@code paper}, {@code velocity}),
     *                empty for any loader; needed for projects that publish separate builds per loader
     * @param executor the executor for HTTP work, or null for the shared update thread
     */
    public ModrinthProvider(@NotNull String project, boolean includePreReleases, @NotNull List<String> loaders, @Nullable Executor executor) {
        this(project, includePreReleases, loaders, new Http(executor), API);
    }

    ModrinthProvider(String project, boolean includePreReleases, List<String> loaders, Http http, URI api) {
        this.project = requireNonNull(project, "Project cannot be null");
        this.includePreReleases = includePreReleases;
        this.loaders = List.copyOf(requireNonNull(loaders, "Loaders cannot be null"));
        this.http = http;
        this.api = api;
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<ReleaseInfo>> latest() {
        String query = loaders.isEmpty() ? "" : "?loaders=" + Releases.encode(loaders.stream()
                .map(loader -> "\"" + loader + "\"")
                .collect(Collectors.joining(",", "[", "]")));
        URI uri = api.resolve("v2/project/" + Releases.encode(project) + "/version" + query);
        return http.get(uri, Releases.JSON).thenApply(body -> body.map(text -> {
            if (!(Releases.parse(text, uri) instanceof List<?> versions)) {
                throw new UpdateCheckException("Expected a JSON array from " + uri);
            }
            return versions.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .filter(version -> includePreReleases || "release".equals(version.get("version_type")))
                    .max(Comparator.comparing(version -> Optional.ofNullable(Releases.instant(version.get("date_published"))).orElse(Instant.EPOCH)))
                    .map(this::toRelease);
        }).flatMap(release -> release));
    }

    private ReleaseInfo toRelease(Map<?, ?> version) {
        String number = Json.string(version.get("version_number"));
        if (number == null || number.isBlank()) {
            throw new UpdateCheckException("Modrinth version without version_number in project " + project);
        }
        String id = Json.string(version.get("id"));
        return new ReleaseInfo(
                SemanticVersion.parse(number),
                Json.string(version.get("name")),
                Releases.uri("https://modrinth.com/project/" + Releases.encode(project) + (id != null ? "/version/" + id : "")),
                Releases.instant(version.get("date_published")));
    }
}
