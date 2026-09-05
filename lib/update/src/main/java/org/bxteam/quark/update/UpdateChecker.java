package org.bxteam.quark.update;

import org.bxteam.quark.common.QuarkLogger;
import org.bxteam.quark.common.SemanticVersion;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static java.util.Objects.requireNonNull;

/**
 * Compares the running version with the newest release of an {@link UpdateProvider}.
 *
 * <pre>{@code
 * UpdateChecker checker = new UpdateChecker(UpdateProvider.modrinth("my-plugin"), getPluginMeta().getVersion());
 * checker.checkAndLog(platform.logger());
 * }</pre>
 */
public final class UpdateChecker {
    private final UpdateProvider provider;
    private final SemanticVersion current;
    private volatile UpdateStatus lastStatus;

    /**
     * @param provider the release source
     * @param current the running version
     */
    public UpdateChecker(@NotNull UpdateProvider provider, @NotNull SemanticVersion current) {
        this.provider = requireNonNull(provider, "Provider cannot be null");
        this.current = requireNonNull(current, "Current version cannot be null");
    }

    /**
     * @param provider the release source
     * @param current the running version, parsed with {@link SemanticVersion#parse(String)}
     */
    public UpdateChecker(@NotNull UpdateProvider provider, @NotNull String current) {
        this(provider, SemanticVersion.parse(current));
    }

    /**
     * Checks for updates. The future never completes exceptionally: failures become {@link UpdateStatus.Unknown}.
     *
     * @return the status
     */
    @NotNull
    public CompletableFuture<UpdateStatus> check() {
        CompletableFuture<Optional<ReleaseInfo>> latest;
        try {
            latest = provider.latest();
        } catch (RuntimeException e) {
            latest = CompletableFuture.failedFuture(e);
        }

        return latest.handle((release, error) -> {
            UpdateStatus status;
            if (error != null) {
                status = new UpdateStatus.Unknown(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
            } else if (release.isPresent() && release.get().version().isNewerThan(current)) {
                status = new UpdateStatus.Outdated(current, release.get());
            } else {
                status = new UpdateStatus.UpToDate(current);
            }
            lastStatus = status;
            return status;
        });
    }

    /**
     * Checks for updates and reports the result to the console: a warning when outdated or when the check
     * failed, a debug message when up to date.
     *
     * @param logger the logger
     * @return the status
     */
    @NotNull
    public CompletableFuture<UpdateStatus> checkAndLog(@NotNull QuarkLogger logger) {
        requireNonNull(logger, "Logger cannot be null");
        return check().whenComplete((status, ignored) -> {
            if (status instanceof UpdateStatus.Outdated outdated) {
                logger.warn("{}", outdated.describe());
            } else if (status instanceof UpdateStatus.Unknown unknown) {
                logger.warn("Could not check for updates: {}", unknown.cause().toString());
            } else {
                logger.debug(() -> "Running the latest version (" + current + ")");
            }
        });
    }

    /**
     * @return the result of the last finished check, or empty if no check finished yet
     */
    @NotNull
    public Optional<UpdateStatus> lastStatus() {
        return Optional.ofNullable(lastStatus);
    }

    /**
     * @return the running version
     */
    @NotNull
    public SemanticVersion currentVersion() {
        return current;
    }
}
