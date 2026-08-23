package org.bxteam.quark.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Logging contract shared by all Quark modules.
 *
 * <p>This is a facade over the logger the server already provides: {@code java.util.logging} on Bukkit,
 * SLF4J on Velocity. Messages use {@code {}} placeholders that are replaced by {@code args} in order,
 * see {@link LogFormat#format(String, Object...)}.</p>
 *
 * <p>{@link JulLogger} is the default implementation, so every module works without a platform adapter.</p>
 */
public interface QuarkLogger {
    /**
     * Logs an informational message.
     *
     * @param message the message with optional {@code {}} placeholders
     * @param args the placeholder values
     */
    void info(@NotNull String message, Object... args);

    /**
     * Logs a warning.
     *
     * @param message the message with optional {@code {}} placeholders
     * @param args the placeholder values
     */
    void warn(@NotNull String message, Object... args);

    /**
     * Logs an error.
     *
     * @param message the message
     * @param t the cause, or null
     */
    void error(@NotNull String message, @Nullable Throwable t);

    /**
     * Logs a debug message. The supplier is only invoked when debug output is enabled,
     * so building the message costs nothing otherwise.
     *
     * @param message the message supplier
     */
    void debug(@NotNull Supplier<String> message);

    /**
     * Creates a logger that writes through this one and prepends {@code [prefix] } to every message.
     *
     * @param prefix the prefix
     * @return the prefixed logger
     */
    @NotNull
    QuarkLogger prefixed(@NotNull String prefix);
}
