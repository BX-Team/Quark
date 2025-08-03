package org.bxteam.quark.relocation;

import org.bxteam.quark.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * Represents a package relocation rule for JAR transformation.
 *
 * <p>Relocations are used to rename packages in library JARs to prevent
 * namespace conflicts when multiple applications use different versions
 * of the same dependency. This is particularly important in plugin
 * environments where multiple plugins might bundle the same libraries.</p>
 *
 * <p>Example: Relocating {@code com.google.gson} to {@code my.plugin.libs.gson}
 * will transform all references to the Google Gson library to use the new package name.</p>
 *
 * @param pattern the original package pattern to match
 * @param relocatedPattern the new package pattern to replace with
 */
public record Relocation(@NotNull String pattern, @NotNull String relocatedPattern) {
    /**
     * Creates a new relocation with pattern validation and normalization.
     *
     * @param pattern the original package pattern
     * @param relocatedPattern the replacement package pattern
     * @throws NullPointerException if either parameter is null
     */
    public Relocation(@NotNull String pattern, @NotNull String relocatedPattern) {
        this.pattern = StringUtils.sanitizePath(requireNonNull(pattern, "Pattern cannot be null"));
        this.relocatedPattern = StringUtils.sanitizePath(requireNonNull(relocatedPattern, "Relocated pattern cannot be null"));
    }

    /**
     * Creates a new relocation builder for complex configuration.
     *
     * @return a new RelocationBuilder instance
     */
    @NotNull
    public static RelocationBuilder builder() {
        return new RelocationBuilder();
    }

    /**
     * Creates a simple relocation from one package to another.
     *
     * @param from the source package
     * @param to the target package
     * @return a new Relocation instance
     */
    @NotNull
    public static Relocation of(@NotNull String from, @NotNull String to) {
        return new Relocation(from, to);
    }

    /**
     * Checks if this relocation would actually change the package name.
     *
     * @return true if the patterns are different
     */
    public boolean isEffective() {
        return !pattern.equals(relocatedPattern);
    }

    /**
     * Gets a human-readable description of this relocation.
     *
     * @return a description string
     */
    @NotNull
    public String getDescription() {
        return pattern + " -> " + relocatedPattern;
    }

    @Override
    public String toString() {
        return getDescription();
    }

    /**
     * Builder for creating Relocation instances with additional configuration.
     */
    public static final class RelocationBuilder {
        private String pattern;
        private String relocatedPattern;

        private RelocationBuilder() {}

        /**
         * Sets the source pattern to relocate from.
         *
         * @param pattern the source pattern
         * @return this builder
         */
        @NotNull
        public RelocationBuilder from(@NotNull String pattern) {
            this.pattern = pattern;
            return this;
        }

        /**
         * Sets the target pattern to relocate to.
         *
         * @param relocatedPattern the target pattern
         * @return this builder
         */
        @NotNull
        public RelocationBuilder to(@NotNull String relocatedPattern) {
            this.relocatedPattern = relocatedPattern;
            return this;
        }

        /**
         * Builds the relocation instance.
         *
         * @return a new Relocation
         * @throws IllegalStateException if required fields are not set
         */
        @NotNull
        public Relocation build() {
            if (pattern == null || relocatedPattern == null) {
                throw new IllegalStateException("Both pattern and relocated pattern must be set");
            }
            return new Relocation(pattern, relocatedPattern);
        }
    }
}
