package org.bxteam.quark.bukkit;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * Converts durations to server ticks.
 */
public final class Ticks {
    /** Length of one server tick in milliseconds. */
    public static final long MILLIS_PER_TICK = 50;

    private Ticks() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    /**
     * @param duration the duration
     * @return the duration in ticks, rounded up
     * @throws IllegalArgumentException if the duration is negative
     */
    public static long of(@NotNull Duration duration) {
        requireNonNull(duration, "Duration cannot be null");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration cannot be negative: " + duration);
        }
        long millis = duration.toMillis();
        return millis / MILLIS_PER_TICK + (millis % MILLIS_PER_TICK == 0 ? 0 : 1);
    }
}
