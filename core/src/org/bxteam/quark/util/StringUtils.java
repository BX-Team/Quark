package org.bxteam.quark.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for string manipulation operations used throughout the Quark library.
 *
 * <p>This class provides common string operations such as path sanitization,
 * pattern replacement, and validation utilities.</p>
 */
@UtilityClass
public class StringUtils {
    private final String BRACE_PLACEHOLDER = "{}";
    private final String DOT_REPLACEMENT = ".";

    /**
     * Replaces all occurrences of "{}" with "." in the provided string.
     * This is commonly used to handle Maven coordinates that use braces
     * to avoid shading conflicts.
     *
     * @param input the string to process
     * @return the string with all "{}" replaced with "."
     * @throws NullPointerException if input is null
     */
    @NotNull
    public String sanitizePath(@NotNull String input) {
        return input.replace(BRACE_PLACEHOLDER, DOT_REPLACEMENT);
    }

    /**
     * Checks if a string is null or empty (after trimming).
     *
     * @param str the string to check
     * @return true if the string is null, empty, or contains only whitespace
     */
    public boolean isBlank(@Nullable String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Checks if a string is not null and not empty (after trimming).
     *
     * @param str the string to check
     * @return true if the string is not null and contains non-whitespace characters
     */
    public boolean isNotBlank(@Nullable String str) {
        return !isBlank(str);
    }

    /**
     * Returns the provided string or a default value if the string is blank.
     *
     * @param str the string to check
     * @param defaultValue the default value to return if str is blank
     * @return the original string if not blank, otherwise the default value
     */
    @NotNull
    public String defaultIfBlank(@Nullable String str, @NotNull String defaultValue) {
        return isBlank(str) ? defaultValue : str;
    }
}
