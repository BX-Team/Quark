package org.bxteam.quark.logger;

/**
 * Enumeration of logging levels in order of increasing severity.
 *
 * <p>This enum defines the standard logging levels used throughout the library.
 * Each level has an associated numeric value that determines its precedence in
 * level-based filtering.</p>
 */
public enum LogLevel {
    /**
     * Debug level for detailed diagnostic information.
     */
    DEBUG(0),

    /**
     * Info level for general informational messages.
     */
    INFO(1),

    /**
     * Warning level for potentially problematic situations.
     */
    WARN(2),

    /**
     * Error level for serious problems and failures.
     */
    ERROR(3);

    /** The numeric level value used for comparison and filtering. */
    private final int level;

    /**
     * Creates a new LogLevel with the specified numeric value.
     *
     * @param level the numeric level value (higher values indicate more severe levels)
     */
    LogLevel(int level) {
        this.level = level;
    }

    /**
     * Determines if this log level is enabled given a threshold level.
     *
     * <p>A log level is considered enabled if its numeric value is greater than
     * or equal to the threshold level's numeric value. This allows for level-based
     * filtering where only messages at or above a certain severity are processed.</p>
     *
     * @param threshold the minimum level that should be logged
     * @return true if this level should be logged given the threshold, false otherwise
     * @throws NullPointerException if threshold is null
     */
    public boolean isEnabled(LogLevel threshold) {
        return this.level >= threshold.level;
    }
}
