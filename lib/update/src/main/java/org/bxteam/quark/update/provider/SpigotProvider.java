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

/**
 * Current version of a <a href="https://www.spigotmc.org/resources/">SpigotMC</a> resource.
 */
public final class SpigotProvider implements UpdateProvider {
    private static final URI API = URI.create("https://api.spigotmc.org/");

    private final long resourceId;
    private final Http http;
    private final URI api;

    /**
     * @param resourceId the resource id, the number at the end of the resource URL
     */
    public SpigotProvider(long resourceId) {
        this(resourceId, null);
    }

    /**
     * @param resourceId the resource id, the number at the end of the resource URL
     * @param executor the executor for HTTP work, or null for the shared update thread
     */
    public SpigotProvider(long resourceId, @Nullable Executor executor) {
        this(resourceId, new Http(executor), API);
    }

    SpigotProvider(long resourceId, Http http, URI api) {
        if (resourceId <= 0) {
            throw new IllegalArgumentException("Resource id must be positive: " + resourceId);
        }
        this.resourceId = resourceId;
        this.http = http;
        this.api = api;
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<ReleaseInfo>> latest() {
        URI uri = api.resolve("simple/0.2/index.php?action=getResource&id=" + resourceId);
        return http.get(uri, Releases.JSON).thenApply(body -> body.map(text -> {
            if (!(Releases.parse(text, uri) instanceof Map<?, ?> resource)) {
                throw new UpdateCheckException("Expected a JSON object from " + uri);
            }
            String version = Json.string(resource.get("current_version"));
            if (version == null || version.isBlank()) {
                Object error = resource.get("error");
                throw new UpdateCheckException("SpigotMC resource " + resourceId + ": " + (error != null ? error : "no current_version"));
            }
            return new ReleaseInfo(
                    SemanticVersion.parse(version),
                    Json.string(resource.get("title")),
                    URI.create("https://www.spigotmc.org/resources/" + resourceId + "/"),
                    null);
        }));
    }
}
