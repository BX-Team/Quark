package org.bxteam.quark.pom;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.dependency.DependencyCollector;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for scanning and collecting dependencies from various sources.
 *
 * <p>Dependency scanners are responsible for analyzing dependency metadata
 * (such as POM files) and recursively collecting all transitive dependencies.
 * This is essential for building a complete dependency graph and ensuring
 * all required libraries are available at runtime.</p>
 *
 * <p>Implementations of this interface should handle:</p>
 * <ul>
 *   <li>Reading dependency metadata from repositories</li>
 *   <li>Parsing dependency declarations</li>
 *   <li>Recursively resolving transitive dependencies</li>
 *   <li>Handling dependency scopes and exclusions</li>
 *   <li>Managing circular dependency detection</li>
 * </ul>
 */
public interface DependencyScanner {
    /**
     * Finds and collects all child dependencies for the given dependency.
     *
     * <p>This method recursively scans the dependency's metadata (typically POM file)
     * to find all transitive dependencies. The collector is used to track which
     * dependencies have already been processed to avoid infinite loops.</p>
     *
     * @param collector the dependency collector to track processed dependencies
     * @param dependency the dependency to scan for children
     * @return the updated dependency collector with all found dependencies
     * @throws NullPointerException if collector or dependency is null
     */
    @NotNull
    DependencyCollector findAllChildren(@NotNull DependencyCollector collector, @NotNull Dependency dependency);

    /**
     * Checks if this scanner supports the given dependency type.
     *
     * <p>Default implementation returns true for all dependencies.
     * Implementations can override this to support only specific types.</p>
     *
     * @param dependency the dependency to check
     * @return true if this scanner can process the dependency
     */
    default boolean supports(@NotNull Dependency dependency) {
        return true;
    }

    /**
     * Gets a human-readable name for this scanner type.
     *
     * @return the scanner type name
     */
    default String getType() {
        return getClass().getSimpleName();
    }
}
