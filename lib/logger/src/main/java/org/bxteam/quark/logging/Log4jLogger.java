package org.bxteam.quark.logging;

import org.apache.logging.log4j.Logger;
import org.bxteam.quark.common.LogFormat;
import org.bxteam.quark.common.QuarkLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * {@link QuarkLogger} on top of Log4j 2.
 */
public final class Log4jLogger implements QuarkLogger {
    private final Logger logger;
    private final String prefix;

    /**
     * @param logger the Log4j logger
     */
    public Log4jLogger(@NotNull Logger logger) {
        this(logger, "");
    }

    private Log4jLogger(Logger logger, String prefix) {
        this.logger = requireNonNull(logger, "Logger cannot be null");
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
        return new Log4jLogger(logger, this.prefix + "[" + requireNonNull(prefix, "Prefix cannot be null") + "] ");
    }

    /**
     * @return the underlying Log4j logger
     */
    @NotNull
    public Logger delegate() {
        return logger;
    }
}
