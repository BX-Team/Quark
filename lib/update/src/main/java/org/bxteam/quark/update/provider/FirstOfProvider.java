package org.bxteam.quark.update.provider;

import org.bxteam.quark.update.ReleaseInfo;
import org.bxteam.quark.update.UpdateCheckException;
import org.bxteam.quark.update.UpdateProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static java.util.Objects.requireNonNull;

/**
 * Asks several providers in order and returns the first release found.
 *
 * <p>A failing or empty provider is skipped. If none finds a release, the result is empty when at least one
 * provider answered, and a failure carrying every error otherwise.</p>
 */
public final class FirstOfProvider implements UpdateProvider {
    private final List<UpdateProvider> providers;

    /**
     * @param providers the providers, in order of preference
     * @throws IllegalArgumentException if the list is empty
     */
    public FirstOfProvider(@NotNull List<UpdateProvider> providers) {
        this.providers = List.copyOf(requireNonNull(providers, "Providers cannot be null"));
        if (this.providers.isEmpty()) {
            throw new IllegalArgumentException("At least one provider is required");
        }
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<ReleaseInfo>> latest() {
        return attempt(0, new ArrayList<>(), false);
    }

    private CompletableFuture<Optional<ReleaseInfo>> attempt(int index, List<Throwable> errors, boolean anyAnswered) {
        if (index == providers.size()) {
            if (anyAnswered) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            UpdateCheckException failure = new UpdateCheckException("All update providers failed");
            errors.forEach(failure::addSuppressed);
            return CompletableFuture.failedFuture(failure);
        }

        CompletableFuture<Optional<ReleaseInfo>> current;
        try {
            current = providers.get(index).latest();
        } catch (RuntimeException e) {
            current = CompletableFuture.failedFuture(e);
        }

        return current.handle((release, error) -> {
            if (error == null && release.isPresent()) {
                return CompletableFuture.completedFuture(release);
            }
            if (error != null) {
                errors.add(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
            }
            return attempt(index + 1, errors, anyAnswered || error == null);
        }).thenCompose(next -> next);
    }
}
