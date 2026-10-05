package org.bxteam.quark.config.backend;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

/**
 * A library a module needs at runtime, such as the YAML parser of {@code quark-config-yaml}.
 *
 * <p>Both package names are needed because the Quark Gradle plugin relocates the library in the shaded JAR:
 * {@code relocatedPackage} must be written as a string literal the Shadow plugin rewrites (it is derived from a
 * class name), {@code originalPackage} in a form it does not rewrite. When they differ, the downloaded JAR is
 * relocated to {@code relocatedPackage} so the shaded code finds it.</p>
 *
 * <pre>{@code
 * String probe = "org.snakeyaml.engine.v2.api.Load"; // rewritten by Shadow
 * new Backend("snakeyaml-engine", "org.snakeyaml:snakeyaml-engine:3.2",
 *         "org{}snakeyaml{}engine".replace("{}", "."),
 *         probe.substring(0, probe.length() - ".v2.api.Load".length()),
 *         probe);
 * }</pre>
 *
 * @param name a human readable name, used in messages
 * @param coordinates the Maven coordinates, {@code group:artifact:version}
 * @param originalPackage the root package of the library as published
 * @param relocatedPackage the root package the shaded code expects
 * @param probeClass a class of the library under {@code relocatedPackage}, used to check whether it is loaded
 */
public record Backend(@NotNull String name, @NotNull String coordinates, @NotNull String originalPackage,
                      @NotNull String relocatedPackage, @NotNull String probeClass) {
    /**
     * @param name a human readable name, used in messages
     * @param coordinates the Maven coordinates, {@code group:artifact:version}
     * @param originalPackage the root package of the library as published
     * @param relocatedPackage the root package the shaded code expects
     * @param probeClass a class of the library under {@code relocatedPackage}, used to check whether it is loaded
     */
    public Backend {
        requireNonNull(name, "Name cannot be null");
        requireNonNull(coordinates, "Coordinates cannot be null");
        requireNonNull(originalPackage, "Original package cannot be null");
        requireNonNull(relocatedPackage, "Relocated package cannot be null");
        requireNonNull(probeClass, "Probe class cannot be null");
        if (coordinates.split(":").length != 3) {
            throw new IllegalArgumentException("Coordinates must be group:artifact:version, got " + coordinates);
        }
    }

    /**
     * @return true if the shaded code expects the library in another package than the published one
     */
    public boolean relocated() {
        return !originalPackage.equals(relocatedPackage);
    }
}
