package org.bxteam.quark.dependency.model;

import org.bxteam.quark.dependency.Dependency;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

/**
 * A successfully resolved dependency with its JAR path.
 *
 * @param dependency the resolved dependency information
 * @param jarPath    the path to the JAR file
 */
public record ResolvedDependency(@NotNull Dependency dependency, @NotNull Path jarPath) {
    /**
     * Creates a new resolved dependency.
     *
     * @throws NullPointerException if any parameter is null
     */
    public ResolvedDependency {
        requireNonNull(dependency, "Dependency cannot be null");
        requireNonNull(jarPath, "JAR path cannot be null");
    }

    /**
     * Returns a string representation of the resolved dependency.
     *
     * @return a string representation
     */
    @Override
    public String toString() {
        return dependency.toShortString() + " -> " + jarPath;
    }
}
