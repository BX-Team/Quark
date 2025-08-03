package org.bxteam.quark.logger;

/**
 * Logging levels in order of severity.
 */
public enum LogLevel {
    DEBUG(0),
    INFO(1),
    WARN(2),
    ERROR(3);

    private final int level;

    LogLevel(int level) {
        this.level = level;
    }

    public boolean isEnabled(LogLevel threshold) {
        return this.level >= threshold.level;
    }
}
