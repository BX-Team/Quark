package org.bxteam.quark.logger.adapters;

import org.bxteam.quark.common.QuarkLogger;
import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.logger.LogLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Writes Quark dependency manager logs to a {@link QuarkLogger}.
 */
public class QuarkLogAdapter implements LogAdapter {
    private final QuarkLogger logger;

    /**
     * @param logger the target logger
     */
    public QuarkLogAdapter(@NotNull QuarkLogger logger) {
        this.logger = requireNonNull(logger, "Logger cannot be null");
    }

    @Override
    public void log(@NotNull LogLevel level, @NotNull String message) {
        log(level, message, null);
    }

    @Override
    public void log(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable throwable) {
        switch (requireNonNull(level, "Level cannot be null")) {
            case DEBUG -> logger.debug(() -> message);
            case INFO -> logger.info(message);
            // QuarkLogger.warn has no throwable parameter, so only its description is kept
            case WARN -> logger.warn(throwable == null ? message : message + " (" + throwable + ")");
            case ERROR -> logger.error(message, throwable);
        }
    }
}
