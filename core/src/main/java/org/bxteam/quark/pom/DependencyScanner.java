package org.bxteam.quark.pom;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.dependency.DependencyCollector;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for scanning dependencies and finding their transitive dependencies.
 *
 * <p>Implementations of this interface are responsible for analyzing dependency
 * metadata (such as POM files) to discover transitive dependencies and add
 * them to the provided collector.</p>
 */
public interface DependencyScanner {
    /**
     * Finds all children (transitive dependencies) for the given dependency
     * and adds them to the collector.
     *
     * <p>This method should recursively process transitive dependencies,
     * ensuring that the entire dependency tree is resolved. The implementation
     * should handle version conflicts and avoid infinite loops.</p>
     *
     * @param collector the collector to add found dependencies to
     * @param dependency the dependency to scan for children
     * @throws org.bxteam.quark.dependency.DependencyException if scanning fails
     */
    void findAllChildren(@NotNull DependencyCollector collector, @NotNull Dependency dependency);

    /**
     * Checks if the scanner can handle the given dependency.
     *
     * <p>This method allows implementations to indicate whether they
     * can process a particular dependency type or format.</p>
     *
     * @param dependency the dependency to check
     * @return true if this scanner can process the dependency
     */
    default boolean canHandle(@NotNull Dependency dependency) {
        return true;
    }

    /**
     * Gets a description of this scanner implementation.
     *
     * @return a human-readable description
     */
    default String getDescription() {
        return getClass().getSimpleName();
    }
}
