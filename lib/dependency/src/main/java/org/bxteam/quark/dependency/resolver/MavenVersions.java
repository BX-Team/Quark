package org.bxteam.quark.dependency.resolver;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * Compares Maven versions, close to Maven's {@code ComparableVersion}: numbers compare numerically
 * ({@code 1.10 > 1.9}), missing parts count as zero ({@code 1.0 == 1.0.0}), and qualifiers are ordered
 * {@code alpha < beta < milestone < rc < snapshot < release < sp}, with unknown qualifiers ({@code jre}) after
 * {@code sp}. A number beats a qualifier at the same position ({@code 1.0.1 > 1.0-rc1}).
 */
public final class MavenVersions {
    /** Orders version strings from oldest to newest. */
    public static final Comparator<String> COMPARATOR = MavenVersions::compare;

    private static final int RELEASE = 6;

    private MavenVersions() {
    }

    /**
     * @param first a version
     * @param second another version
     * @return a negative number, zero or a positive number as {@code first} is older than, equal to or newer
     * than {@code second}
     */
    public static int compare(@NotNull String first, @NotNull String second) {
        List<Object> a = tokens(requireNonNull(first, "Version cannot be null"));
        List<Object> b = tokens(requireNonNull(second, "Version cannot be null"));
        for (int i = 0, size = Math.max(a.size(), b.size()); i < size; i++) {
            Object left = i < a.size() ? a.get(i) : padding(b.get(i));
            Object right = i < b.size() ? b.get(i) : padding(a.get(i));
            int result = compareTokens(left, right);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    /**
     * @return the newer of two versions, {@code first} when they are equal
     */
    @NotNull
    public static String newer(@NotNull String first, @NotNull String second) {
        return compare(first, second) >= 0 ? first : second;
    }

    private static Object padding(Object other) {
        // a missing part is a zero next to a number and a release next to a qualifier
        return other instanceof Long ? (Object) 0L : "";
    }

    private static int compareTokens(Object left, Object right) {
        if (left instanceof Long l && right instanceof Long r) {
            return Long.compare(l, r);
        }
        if (left instanceof Long) {
            return 1;
        }
        if (right instanceof Long) {
            return -1;
        }
        String l = (String) left;
        String r = (String) right;
        int rank = Integer.compare(rank(l), rank(r));
        return rank != 0 ? rank : l.compareTo(r);
    }

    private static int rank(String qualifier) {
        return switch (qualifier) {
            case "alpha", "a" -> 1;
            case "beta", "b" -> 2;
            case "milestone", "m" -> 3;
            case "rc", "cr" -> 4;
            case "snapshot" -> 5;
            case "", "ga", "final", "release" -> RELEASE;
            case "sp" -> 7;
            default -> 8;
        };
    }

    private static List<Object> tokens(String version) {
        List<Object> tokens = new ArrayList<>();
        String lower = version.trim().toLowerCase(Locale.ROOT);
        StringBuilder current = new StringBuilder();
        boolean digits = false;
        for (int i = 0; i <= lower.length(); i++) {
            char c = i < lower.length() ? lower.charAt(i) : '.';
            boolean separator = c == '.' || c == '-' || c == '_' || c == '+';
            boolean digit = Character.isDigit(c);
            if (separator || (current.length() > 0 && digit != digits)) {
                if (current.length() > 0) {
                    tokens.add(token(current.toString(), digits));
                    current.setLength(0);
                }
                if (separator) {
                    continue;
                }
            }
            current.append(c);
            digits = digit;
        }
        // trailing zeros and release qualifiers do not change the version: 1.0.0 == 1, 1.0-final == 1.0
        while (!tokens.isEmpty()) {
            Object last = tokens.get(tokens.size() - 1);
            if ((last instanceof Long number && number == 0L) || (last instanceof String qualifier && rank(qualifier) == RELEASE)) {
                tokens.remove(tokens.size() - 1);
            } else {
                break;
            }
        }
        return tokens;
    }

    private static Object token(String text, boolean digits) {
        if (digits) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return text;
            }
        }
        return text;
    }
}
