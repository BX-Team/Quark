package org.bxteam.quark.logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.requireNonNull;

/**
 * Simple logger that delegates to a LogAdapter.
 */
public class Logger {
    private final LogAdapter adapter;
    private volatile LogLevel level = LogLevel.INFO;

    public Logger(@NotNull LogAdapter adapter) {
        this.adapter = requireNonNull(adapter, "Log adapter cannot be null");
    }

    public void setLevel(@NotNull LogLevel level) {
        this.level = requireNonNull(level, "Log level cannot be null");
    }

    @NotNull
    public LogLevel getLevel() {
        return level;
    }

    public boolean isDebugEnabled() {
        return LogLevel.DEBUG.isEnabled(level);
    }

    public boolean isInfoEnabled() {
        return LogLevel.INFO.isEnabled(level);
    }

    public boolean isWarnEnabled() {
        return LogLevel.WARN.isEnabled(level);
    }

    public boolean isErrorEnabled() {
        return LogLevel.ERROR.isEnabled(level);
    }

    public void debug(@NotNull String message) {
        if (isDebugEnabled()) {
            adapter.log(LogLevel.DEBUG, message);
        }
    }

    public void debug(@NotNull String message, @Nullable Throwable throwable) {
        if (isDebugEnabled()) {
            adapter.log(LogLevel.DEBUG, message, throwable);
        }
    }

    public void info(@NotNull String message) {
        if (isInfoEnabled()) {
            adapter.log(LogLevel.INFO, message);
        }
    }

    public void info(@NotNull String message, @Nullable Throwable throwable) {
        if (isInfoEnabled()) {
            adapter.log(LogLevel.INFO, message, throwable);
        }
    }

    public void warn(@NotNull String message) {
        if (isWarnEnabled()) {
            adapter.log(LogLevel.WARN, message);
        }
    }

    public void warn(@NotNull String message, @Nullable Throwable throwable) {
        if (isWarnEnabled()) {
            adapter.log(LogLevel.WARN, message, throwable);
        }
    }

    public void error(@NotNull String message) {
        if (isErrorEnabled()) {
            adapter.log(LogLevel.ERROR, message);
        }
    }

    public void error(@NotNull String message, @Nullable Throwable throwable) {
        if (isErrorEnabled()) {
            adapter.log(LogLevel.ERROR, message, throwable);
        }
    }
}
