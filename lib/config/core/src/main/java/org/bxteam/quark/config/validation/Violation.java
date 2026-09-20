package org.bxteam.quark.config.validation;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * A failed check of a configuration value.
 *
 * @param path the path of the value, keys joined with dots
 * @param message what is wrong
 */
public record Violation(@NotNull String path, @NotNull String message) {
    /**
     * @param path the path of the value, keys joined with dots
     * @param message what is wrong
     */
    public Violation {
        requireNonNull(path, "Path cannot be null");
        requireNonNull(message, "Message cannot be null");
    }

    @Override
    public String toString() {
        return path + ": " + message;
    }
}
