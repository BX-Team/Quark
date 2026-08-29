package org.bxteam.quark.velocity;

import org.bxteam.quark.common.LogFormat;
import org.bxteam.quark.common.QuarkLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * {@link QuarkLogger} on top of the SLF4J logger Velocity gives every plugin.
 */
final class Slf4jQuarkLogger implements QuarkLogger {
    private final Logger logger;
    private final String prefix;

    Slf4jQuarkLogger(@NotNull Logger logger) {
        this(logger, "");
    }

    private Slf4jQuarkLogger(Logger logger, String prefix) {
        this.logger = logger;
        this.prefix = prefix;
    }

    @Override
    public void info(@NotNull String message, Object... args) {
        if (logger.isInfoEnabled()) {
            logger.info(prefix + LogFormat.format(message, args));
        }
    }

    @Override
    public void warn(@NotNull String message, Object... args) {
        if (logger.isWarnEnabled()) {
            logger.warn(prefix + LogFormat.format(message, args));
        }
    }

    @Override
    public void error(@NotNull String message, @Nullable Throwable t) {
        logger.error(prefix + message, t);
    }

    @Override
    public void debug(@NotNull Supplier<String> message) {
        if (logger.isDebugEnabled()) {
            logger.debug(prefix + message.get());
        }
    }

    @Override
    @NotNull
    public QuarkLogger prefixed(@NotNull String prefix) {
        return new Slf4jQuarkLogger(logger, this.prefix + "[" + requireNonNull(prefix, "Prefix cannot be null") + "] ");
    }
}
