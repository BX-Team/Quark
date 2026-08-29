package org.bxteam.quark.logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for adapting Quark logging to different logging frameworks.
 */
public interface LogAdapter {
    /**
     * Logs a message at the specified level.
     *
     * @param level the log level
     * @param message the message to log
     */
    void log(@NotNull LogLevel level, @NotNull String message);

    /**
     * Logs a message with an exception at the specified level.
     *
     * @param level the log level
     * @param message the message to log
     * @param throwable the exception to log
     */
    void log(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable throwable);
}
