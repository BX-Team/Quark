package org.bxteam.quark.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * A version string compared by <a href="https://semver.org">Semantic Versioning 2.0</a> precedence.
 *
 * <p>Parsing is lenient: a leading {@code v} is ignored and missing minor/patch parts default to
 * zero ({@code 1.21} equals {@code 1.21.0}). Strings that are not semantic versions at all
 * (for example {@code 1.2.3.4} or {@code build-55}) are still accepted; such versions are compared
 * lexicographically by their raw value, see {@link #isSemantic()}.</p>
 *
 * <p>Build metadata ({@code +...}) is kept for display but ignored by {@link #compareTo(SemanticVersion)},
 * {@link #equals(Object)} and {@link #hashCode()}.</p>
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {
    private static final Pattern PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)(?:\\.(0|[1-9]\\d*))?(?:\\.(0|[1-9]\\d*))?"
                    + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                    + "(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$");

    private final String raw;
    private final boolean semantic;
    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String build;

    private SemanticVersion(String raw, boolean semantic, int major, int minor, int patch,
                            @Nullable String preRelease, @Nullable String build) {
        this.raw = raw;
        this.semantic = semantic;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
        this.build = build;
    }

    /**
     * Parses a version string.
     *
     * @param version the version string
     * @return the parsed version, never null
     * @throws NullPointerException if version is null
     * @throws IllegalArgumentException if version is blank
     */
    @NotNull
    public static SemanticVersion parse(@NotNull String version) {
        String trimmed = requireNonNull(version, "Version cannot be null").trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Version cannot be blank");
        }

        String candidate = trimmed.length() > 1 && (trimmed.charAt(0) == 'v' || trimmed.charAt(0) == 'V')
                ? trimmed.substring(1)
                : trimmed;

        Matcher matcher = PATTERN.matcher(candidate);
        if (!matcher.matches()) {
            return new SemanticVersion(trimmed, false, 0, 0, 0, null, null);
        }

        try {
            return new SemanticVersion(
                    trimmed,
                    true,
                    Integer.parseInt(matcher.group(1)),
                    matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0,
                    matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0,
                    matcher.group(4),
                    matcher.group(5));
        } catch (NumberFormatException e) {
            // numeric part does not fit into an int
            return new SemanticVersion(trimmed, false, 0, 0, 0, null, null);
        }
    }

    /**
     * Creates a release version from its numeric parts.
     *
     * @param major the major version
     * @param minor the minor version
     * @param patch the patch version
     * @return the version
     * @throws IllegalArgumentException if any part is negative
     */
    @NotNull
    public static SemanticVersion of(int major, int minor, int patch) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("Version parts cannot be negative");
        }
        return new SemanticVersion(major + "." + minor + "." + patch, true, major, minor, patch, null, null);
    }

    /**
     * Whether the raw value is a semantic version. Non-semantic versions are compared lexicographically.
     *
     * @return true if the raw value follows semantic versioning
     */
    public boolean isSemantic() {
        return semantic;
    }

    /**
     * @return the major version, or 0 for non-semantic versions
     */
    public int major() {
        return major;
    }

    /**
     * @return the minor version, or 0 for non-semantic versions
     */
    public int minor() {
        return minor;
    }

    /**
     * @return the patch version, or 0 for non-semantic versions
     */
    public int patch() {
        return patch;
    }

    /**
     * @return the pre-release identifiers (e.g. {@code beta.2}), or null if this is a release
     */
    @Nullable
    public String preRelease() {
        return preRelease;
    }

    /**
     * @return the build metadata (e.g. {@code 20250101}), or null if absent
     */
    @Nullable
    public String build() {
        return build;
    }

    /**
     * @return true if this is a semantic version with pre-release identifiers
     */
    public boolean isPreRelease() {
        return preRelease != null;
    }

    /**
     * @return the version string as it was parsed
     */
    @NotNull
    public String raw() {
        return raw;
    }

    /**
     * @param other the version to compare with
     * @return true if this version has higher precedence than {@code other}
     */
    public boolean isNewerThan(@NotNull SemanticVersion other) {
        return compareTo(other) > 0;
    }

    /**
     * @param other the version to compare with
     * @return true if this version has lower precedence than {@code other}
     */
    public boolean isOlderThan(@NotNull SemanticVersion other) {
        return compareTo(other) < 0;
    }

    /**
     * @param other the version to compare with
     * @return true if this version has the same or higher precedence than {@code other}
     */
    public boolean isAtLeast(@NotNull SemanticVersion other) {
        return compareTo(other) >= 0;
    }

    @Override
    public int compareTo(@NotNull SemanticVersion other) {
        requireNonNull(other, "Other version cannot be null");

        if (!semantic || !other.semantic) {
            return raw.compareTo(other.raw);
        }

        int result = Integer.compare(major, other.major);
        if (result != 0) return result;
        result = Integer.compare(minor, other.minor);
        if (result != 0) return result;
        result = Integer.compare(patch, other.patch);
        if (result != 0) return result;

        return comparePreRelease(preRelease, other.preRelease);
    }

    private static int comparePreRelease(@Nullable String left, @Nullable String right) {
        if (left == null || right == null) {
            // a release has higher precedence than any pre-release
            return left == null ? (right == null ? 0 : 1) : -1;
        }

        String[] leftIds = left.split("\\.");
        String[] rightIds = right.split("\\.");
        int common = Math.min(leftIds.length, rightIds.length);

        for (int i = 0; i < common; i++) {
            int result = compareIdentifier(leftIds[i], rightIds[i]);
            if (result != 0) return result;
        }

        return Integer.compare(leftIds.length, rightIds.length);
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumeric = isNumeric(left);
        boolean rightNumeric = isNumeric(right);

        if (leftNumeric && rightNumeric) {
            int result = Integer.compare(left.length(), right.length());
            return result != 0 ? result : left.compareTo(right);
        }
        if (leftNumeric != rightNumeric) {
            // numeric identifiers have lower precedence than alphanumeric ones
            return leftNumeric ? -1 : 1;
        }
        return left.compareTo(right);
    }

    private static boolean isNumeric(String identifier) {
        for (int i = 0; i < identifier.length(); i++) {
            if (!Character.isDigit(identifier.charAt(i))) {
                return false;
            }
        }
        return !identifier.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SemanticVersion that)) return false;
        return compareTo(that) == 0;
    }

    @Override
    public int hashCode() {
        return semantic ? Objects.hash(major, minor, patch, preRelease) : raw.hashCode();
    }

    @Override
    public String toString() {
        return raw;
    }
}
