package org.bxteam.quark.logger.adapters;

import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.logger.LogLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * Adapter for Java's built-in logging framework.
 */
public class JavaLogAdapter implements LogAdapter {
    private final Logger logger;

    public JavaLogAdapter(@NotNull Logger logger) {
        this.logger = requireNonNull(logger, "logger");
    }

    @Override
    public void log(@NotNull LogLevel level, @NotNull String message) {
        logger.log(convertLevel(level), message);
    }

    @Override
    public void log(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable throwable) {
        logger.log(convertLevel(level), message, throwable);
    }

    private Level convertLevel(LogLevel level) {
        return switch (level) {
            case DEBUG -> Level.FINE;
            case WARN -> Level.WARNING;
            case ERROR -> Level.SEVERE;
            default -> Level.INFO;
        };
    }
}
