package org.bxteam.quark.fabric.logger.adapters;

import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.logger.LogLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Log adapter for Fabric mods using SLF4J.
 */
public class FabricLogAdapter implements LogAdapter {
    private final Logger logger;

    /**
     * Creates a new Fabric log adapter that logs to a SLF4J logger.
     *
     * @param logger the SLF4J logger to wrap
     */
    public FabricLogAdapter(@NotNull Logger logger) {
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
