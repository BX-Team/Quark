package org.bxteam.quark.logging;

import org.bxteam.quark.common.QuarkLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * Global debug switch.
 *
 * <p>Server loggers usually drop debug output, so a plugin's {@code debug: true} setting would have no effect.
 * Loggers wrapped with {@link #wrap(QuarkLogger)} print debug messages at info level with a {@code [DEBUG]}
 * prefix while the switch is on, and pass them to the backend's debug level otherwise.</p>
 *
 * <p>Quark is relocated into every plugin, so the switch is per plugin, not per server.</p>
 */
public final class DebugSwitch {
    private static volatile boolean enabled;

    private DebugSwitch() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    /**
     * @param enabled whether debug messages are printed at info level
     */
    public static void setEnabled(boolean enabled) {
        DebugSwitch.enabled = enabled;
    }

    /**
     * @return whether debug messages are printed at info level
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Wraps a logger so its debug output follows this switch.
     *
     * @param logger the logger
     * @return the wrapped logger, or {@code logger} itself if it is already wrapped
     */
    @NotNull
    public static QuarkLogger wrap(@NotNull QuarkLogger logger) {
        requireNonNull(logger, "Logger cannot be null");
        return logger instanceof Switchable ? logger : new Switchable(logger);
    }

    private record Switchable(QuarkLogger delegate) implements QuarkLogger {
        @Override
        public void info(@NotNull String message, Object... args) {
            delegate.info(message, args);
        }

        @Override
        public void warn(@NotNull String message, Object... args) {
            delegate.warn(message, args);
        }

        @Override
        public void error(@NotNull String message, @Nullable Throwable t) {
            delegate.error(message, t);
        }

        @Override
        public void debug(@NotNull Supplier<String> message) {
            if (enabled) {
                // passed as an argument, so placeholders inside the message are not expanded again
                delegate.info("[DEBUG] {}", message.get());
            } else {
                delegate.debug(message);
            }
        }

        @Override
        @NotNull
        public QuarkLogger prefixed(@NotNull String prefix) {
            return new Switchable(delegate.prefixed(prefix));
        }
    }
}
