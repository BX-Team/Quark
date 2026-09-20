package org.bxteam.quark.config.annotation;

import org.jetbrains.annotations.NotNull;

/**
 * Naming styles of {@link NameStrategy}.
 */
public enum NameStyle {
    /** The field name as is: {@code maxPlayers}. */
    IDENTITY,
    /** Lower case words joined with hyphens: {@code max-players}. */
    HYPHEN_CASE,
    /** Lower case words joined with underscores: {@code max_players}. */
    SNAKE_CASE;

    /**
     * Applies this style to a camel case field name. Acronyms stay one word: {@code maxHTTPConnections}
     * becomes {@code max-http-connections}.
     *
     * @param fieldName the field name
     * @return the key
     */
    @NotNull
    public String apply(@NotNull String fieldName) {
        return switch (this) {
            case IDENTITY -> fieldName;
            case HYPHEN_CASE -> join(fieldName, '-');
            case SNAKE_CASE -> join(fieldName, '_');
        };
    }

    private static String join(String name, char separator) {
        StringBuilder result = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_' || c == '-') {
                if (result.length() > 0 && result.charAt(result.length() - 1) != separator) {
                    result.append(separator);
                }
                continue;
            }
            if (Character.isUpperCase(c) && i > 0) {
                char previous = name.charAt(i - 1);
                boolean nextLower = i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1));
                if ((Character.isLowerCase(previous) || Character.isDigit(previous) || (Character.isUpperCase(previous) && nextLower))
                        && result.length() > 0 && result.charAt(result.length() - 1) != separator) {
                    result.append(separator);
                }
            }
            result.append(Character.toLowerCase(c));
        }
        return result.toString();
    }
}
