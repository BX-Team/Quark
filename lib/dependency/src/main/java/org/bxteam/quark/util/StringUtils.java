package org.bxteam.quark.util;

import org.jetbrains.annotations.NotNull;

/**
 * Utility class for string manipulation operations used throughout the Quark library.
 *
 * <p>This class provides path sanitization for relocation patterns.</p>
 */
public final class StringUtils {
    private static final String BRACE_PLACEHOLDER = "{}";
    private static final String DOT_REPLACEMENT = ".";

    private StringUtils() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

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
    public static String sanitizePath(@NotNull String input) {
        return input.replace(BRACE_PLACEHOLDER, DOT_REPLACEMENT);
    }
}
