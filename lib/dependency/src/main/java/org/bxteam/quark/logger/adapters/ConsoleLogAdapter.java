package org.bxteam.quark.logger.adapters;

import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.logger.LogLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple console log adapter that prints to System.out/System.err.
 */
public class ConsoleLogAdapter implements LogAdapter {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public void log(@NotNull LogLevel level, @NotNull String message) {
        log(level, message, null);
    }

    @Override
    public void log(@NotNull LogLevel level, @NotNull String message, @Nullable Throwable throwable) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        String logMessage = String.format("[%s] [%s] %s", timestamp, level.name(), message);

        PrintStream stream = (level == LogLevel.ERROR) ? System.err : System.out;
        stream.println(logMessage);

        if (throwable != null) {
            throwable.printStackTrace(stream);
        }
    }
}
