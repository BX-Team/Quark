package org.bxteam.quark.common;

import org.jetbrains.annotations.NotNull;

/**
 * Formats log messages with SLF4J-style {@code {}} placeholders.
 */
public final class LogFormat {
    private static final String PLACEHOLDER = "{}";

    private LogFormat() {
        throw new UnsupportedOperationException("This class cannot be instantiated");
    }

    /**
     * Replaces each {@code {}} in {@code message} with the next argument. Extra arguments are ignored,
     * placeholders without an argument are left as is.
     *
     * @param message the message
     * @param args the arguments
     * @return the formatted message
     */
    @NotNull
    public static String format(@NotNull String message, Object... args) {
        if (args == null || args.length == 0 || !message.contains(PLACEHOLDER)) {
            return message;
        }

        StringBuilder result = new StringBuilder(message.length() + 16 * args.length);
        int argIndex = 0;
        int from = 0;
        int index;

        while (argIndex < args.length && (index = message.indexOf(PLACEHOLDER, from)) != -1) {
            result.append(message, from, index).append(args[argIndex++]);
            from = index + PLACEHOLDER.length();
        }

        return result.append(message, from, message.length()).toString();
    }
}
