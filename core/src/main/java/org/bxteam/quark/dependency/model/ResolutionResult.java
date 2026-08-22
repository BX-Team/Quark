package org.bxteam.quark.dependency.model;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Result of dependency resolution.
 */
@Getter
public final class ResolutionResult {
    private final List<ResolvedDependency> resolvedDependencies;
    private final List<String> errors;

    /**
     * Creates a new resolution result.
     *
     * @param resolvedDependencies the list of successfully resolved dependencies
     * @param errors               the list of error messages encountered during resolution
     * @throws NullPointerException if any parameter is null
     */
    public ResolutionResult(@NotNull List<ResolvedDependency> resolvedDependencies, @NotNull List<String> errors) {
        this.resolvedDependencies = List.copyOf(resolvedDependencies);
        this.errors = List.copyOf(errors);
    }

    /**
     * Checks if any errors occurred during resolution.
     *
     * @return true if there are error messages, false otherwise
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    /**
     * Gets the total number of resolved dependencies.
     *
     * @return the number of resolved dependencies
     */
    public int getDependencyCount() {
        return resolvedDependencies.size();
    }
}
