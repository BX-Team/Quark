package org.bxteam.quark.update.internal;

import org.bxteam.quark.update.UpdateCheckException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Objects.requireNonNull;

/**
 * HTTP GET on top of {@link HttpClient}. Internal API, not covered by compatibility guarantees.
 */
public final class Http {
    private static final String USER_AGENT = "BX-Team/Quark (+https://github.com/BX-Team/Quark)";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient client;

    /**
     * @param executor the executor for HTTP work, or null for a shared daemon thread
     */
    public Http(@Nullable Executor executor) {
        this.client = HttpClient.newBuilder()
                .executor(executor != null ? executor : DefaultExecutor.INSTANCE)
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Fetches a URL.
     *
     * @param uri the URL
     * @param accept the Accept header
     * @return the body, empty on HTTP 404; fails with {@link UpdateCheckException} on any other non-2xx status
     */
    @NotNull
    public CompletableFuture<Optional<String>> get(@NotNull URI uri, @NotNull String accept) {
        HttpRequest request = HttpRequest.newBuilder(requireNonNull(uri, "URI cannot be null"))
                .timeout(TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            int status = response.statusCode();
            if (status == 404) {
                return Optional.empty();
            }
            if (status < 200 || status >= 300) {
                throw new UpdateCheckException("HTTP " + status + " from " + uri);
            }
            return Optional.of(response.body());
        });
    }

    private static final class DefaultExecutor {
        static final ExecutorService INSTANCE = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Quark Update Checker");
            thread.setDaemon(true);
            return thread;
        });
    }
}
