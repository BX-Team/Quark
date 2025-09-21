package org.bxteam.quark.logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * A simple logger that delegates log operations to a LogAdapter implementation.
 *
 * <p>This logger provides level-based filtering and convenient methods for
 * logging at different severity levels. It supports optional throwable
 * parameters for exception logging.</p>
 *
 * <p>The logger is thread-safe and can be used concurrently from multiple threads.
 * The log level can be changed at runtime using {@link #setLevel(LogLevel)}.</p>
 *
 * @see LogAdapter
 * @see LogLevel
 */
public class Logger {
    /** The underlying log adapter that performs the actual logging operations. */
    private final LogAdapter adapter;

    /** The current minimum log level for this logger instance. */
    private volatile LogLevel level = LogLevel.INFO;

    /**
     * Creates a new Logger with the specified LogAdapter.
     *
     * @param adapter the log adapter to delegate logging operations to
     * @throws NullPointerException if adapter is null
     */
    public Logger(@NotNull LogAdapter adapter) {
        this.adapter = requireNonNull(adapter, "Log adapter cannot be null");
    }

    /**
     * Sets the minimum log level for this logger.
     *
     * <p>Only log messages at or above this level will be processed.
     * This change takes effect immediately and is thread-safe.</p>
     *
     * @param level the new minimum log level
     * @throws NullPointerException if level is null
     */
    public void setLevel(@NotNull LogLevel level) {
        this.level = requireNonNull(level, "Log level cannot be null");
    }

    /**
     * Gets the current minimum log level for this logger.
     *
     * @return the current log level
     */
    @NotNull
    public LogLevel getLevel() {
        return level;
    }

    /**
     * Checks if DEBUG level logging is enabled.
     *
     * @return true if DEBUG messages will be logged, false otherwise
     */
    public boolean isDebugEnabled() {
        return LogLevel.DEBUG.isEnabled(level);
    }

    /**
     * Checks if INFO level logging is enabled.
     *
     * @return true if INFO messages will be logged, false otherwise
     */
    public boolean isInfoEnabled() {
        return LogLevel.INFO.isEnabled(level);
    }

    /**
     * Checks if WARN level logging is enabled.
     *
     * @return true if WARN messages will be logged, false otherwise
     */
    public boolean isWarnEnabled() {
        return LogLevel.WARN.isEnabled(level);
    }

    /**
     * Checks if ERROR level logging is enabled.
     *
     * @return true if ERROR messages will be logged, false otherwise
     */
    public boolean isErrorEnabled() {
        return LogLevel.ERROR.isEnabled(level);
    }

    /**
     * Logs a message at DEBUG level.
     *
     * <p>The message will only be logged if DEBUG level is enabled.</p>
     *
     * @param message the message to log
     * @throws NullPointerException if message is null
     */
    public void debug(@NotNull String message) {
        if (isDebugEnabled()) {
            adapter.log(LogLevel.DEBUG, message);
        }
    }

    /**
     * Logs a message with an optional throwable at DEBUG level.
     *
     * <p>The message will only be logged if DEBUG level is enabled.</p>
     *
     * @param message the message to log
     * @param throwable the throwable to log, or null
     * @throws NullPointerException if message is null
     */
    public void debug(@NotNull String message, @Nullable Throwable throwable) {
        if (isDebugEnabled()) {
            adapter.log(LogLevel.DEBUG, message, throwable);
        }
    }

    /**
     * Logs a message at INFO level.
     *
     * <p>The message will only be logged if INFO level is enabled.</p>
     *
     * @param message the message to log
     * @throws NullPointerException if message is null
     */
    public void info(@NotNull String message) {
        if (isInfoEnabled()) {
            adapter.log(LogLevel.INFO, message);
        }
    }

    /**
     * Logs a message with an optional throwable at INFO level.
     *
     * <p>The message will only be logged if INFO level is enabled.</p>
     *
     * @param message the message to log
     * @param throwable the throwable to log, or null
     * @throws NullPointerException if message is null
     */
    public void info(@NotNull String message, @Nullable Throwable throwable) {
        if (isInfoEnabled()) {
            adapter.log(LogLevel.INFO, message, throwable);
        }
    }

    /**
     * Logs a message at WARN level.
     *
     * <p>The message will only be logged if WARN level is enabled.</p>
     *
     * @param message the message to log
     * @throws NullPointerException if message is null
     */
    public void warn(@NotNull String message) {
        if (isWarnEnabled()) {
            adapter.log(LogLevel.WARN, message);
        }
    }

    /**
     * Logs a message with an optional throwable at WARN level.
     *
     * <p>The message will only be logged if WARN level is enabled.</p>
     *
     * @param message the message to log
     * @param throwable the throwable to log, or null
     * @throws NullPointerException if message is null
     */
    public void warn(@NotNull String message, @Nullable Throwable throwable) {
        if (isWarnEnabled()) {
            adapter.log(LogLevel.WARN, message, throwable);
        }
    }

    /**
     * Logs a message at ERROR level.
     *
     * <p>The message will only be logged if ERROR level is enabled.</p>
     *
     * @param message the message to log
     * @throws NullPointerException if message is null
     */
    public void error(@NotNull String message) {
        if (isErrorEnabled()) {
            adapter.log(LogLevel.ERROR, message);
        }
    }

    /**
     * Logs a message with an optional throwable at ERROR level.
     *
     * <p>The message will only be logged if ERROR level is enabled.</p>
     *
     * @param message the message to log
     * @param throwable the throwable to log, or null
     * @throws NullPointerException if message is null
     */
    public void error(@NotNull String message, @Nullable Throwable throwable) {
        if (isErrorEnabled()) {
            adapter.log(LogLevel.ERROR, message, throwable);
        }
    }
}
