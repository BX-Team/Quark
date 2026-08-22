package org.bxteam.quark.sponge.logger.adapters;

import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.logger.LogLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.apache.logging.log4j.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Log adapter for Sponge plugins using Log4j loggers.
 *
 * <p>This adapter bridges Quark's logging system with Sponge's Log4j-based
 * logging infrastructure.</p>
 */
public class SpongeLogAdapter implements LogAdapter {
    private final Logger logger;

    /**
     * Creates a new Sponge log adapter that logs to a Log4j logger.
     *
     * @param logger the Log4j logger to wrap (typically from Sponge plugin)
     * @throws NullPointerException if logger is null
     */
    public SpongeLogAdapter(@NotNull Logger logger) {
        this.logger = requireNonNull(logger, "Logger cannot be null");
    }

    @Override
    public void log(@NotNull LogLevel level, @NotNull String message) {
        switch (requireNonNull(level, "level")) {
            case DEBUG:
                logger.debug(message);
                break;
            case INFO:
                logger.info(message);
                break;
            case WARN:
                logger.warn(message);
                break;
            case ERROR:
                logger.error(message);
                break;
        }
    }

    @Override
    public void log(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable throwable) {
        switch (requireNonNull(level, "level")) {
            case DEBUG:
                logger.debug(message, throwable);
                break;
            case INFO:
                logger.info(message, throwable);
                break;
            case WARN:
                logger.warn(message, throwable);
                break;
            case ERROR:
                logger.error(message, throwable);
                break;
        }
    }
}
