package org.bxteam.quark.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

/**
 * Utility class for string manipulation operations used throughout the Quark library.
 *
 * <p>This class provides path sanitization for relocation patterns.</p>
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
}
