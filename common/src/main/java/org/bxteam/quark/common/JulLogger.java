package org.bxteam.quark.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.Objects.requireNonNull;

/**
 * {@link QuarkLogger} on top of {@code java.util.logging}. Debug messages are logged at {@link Level#FINE}.
 */
public final class JulLogger implements QuarkLogger {
    private final Logger logger;
    private final String prefix;

    /**
     * Creates a logger that writes to the given JUL logger.
     *
     * @param logger the JUL logger
     */
    public JulLogger(@NotNull Logger logger) {
        this(logger, "");
    }

    private JulLogger(Logger logger, String prefix) {
        this.logger = requireNonNull(logger, "Logger cannot be null");
        this.prefix = prefix;
    }

    /**
     * Creates a logger that writes to {@link Logger#getLogger(String)}.
     *
     * @param name the logger name
     * @return the logger
     */
    @NotNull
    public static JulLogger of(@NotNull String name) {
        return new JulLogger(Logger.getLogger(requireNonNull(name, "Name cannot be null")));
    }

    @Override
    public void info(@NotNull String message, Object... args) {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, prefix + LogFormat.format(message, args));
        }
    }

    @Override
    public void warn(@NotNull String message, Object... args) {
        if (logger.isLoggable(Level.WARNING)) {
            logger.log(Level.WARNING, prefix + LogFormat.format(message, args));
        }
    }

    @Override
    public void error(@NotNull String message, @Nullable Throwable t) {
        logger.log(Level.SEVERE, prefix + message, t);
    }

    @Override
    public void debug(@NotNull Supplier<String> message) {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, prefix + message.get());
        }
    }

    @Override
    @NotNull
    public QuarkLogger prefixed(@NotNull String prefix) {
        return new JulLogger(logger, this.prefix + "[" + requireNonNull(prefix, "Prefix cannot be null") + "] ");
    }

    /**
     * @return the underlying JUL logger
     */
    @NotNull
    public Logger delegate() {
        return logger;
    }
}
