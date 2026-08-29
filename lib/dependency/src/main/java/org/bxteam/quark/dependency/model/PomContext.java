package org.bxteam.quark.dependency.model;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.pom.model.PomInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Context for POM processing including all resolved dependencies.
 *
 * @param pomInfo         the POM information
 * @param allDependencies the list of all resolved dependencies
 */
public record PomContext(@NotNull PomInfo pomInfo, @NotNull List<Dependency> allDependencies) {
    /**
     * Creates a new POM context.
     *
     * @throws NullPointerException if any parameter is null
     */
    public PomContext {
        requireNonNull(pomInfo, "POM info cannot be null");
        allDependencies = List.copyOf(requireNonNull(allDependencies, "Dependencies cannot be null"));
    }
}
