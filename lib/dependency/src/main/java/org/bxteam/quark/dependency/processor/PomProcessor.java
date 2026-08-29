package org.bxteam.quark.dependency.processor;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.dependency.cache.DependencyCache;
import org.bxteam.quark.dependency.downloader.DependencyDownloader;
import org.bxteam.quark.dependency.model.PomContext;
import org.bxteam.quark.dependency.resolver.VersionResolver;
import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.pom.model.ParentInfo;
import org.bxteam.quark.pom.model.PomInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Processes POM files including parent hierarchy and dependency resolution.
 */
public class PomProcessor {
    private static final int MAX_PARENT_HIERARCHY_DEPTH = 10;

    private final Logger logger;
    private final DependencyDownloader downloader;
    private final VersionResolver versionResolver;
    private final DependencyCache cache;
    private final boolean includeDependencyManagement;
    private final boolean skipOptionalDependencies;
    private final boolean skipTestDependencies;
    private final java.util.Set<String> excludedGroupIds;
    private final List<Pattern> excludedArtifactPatterns;

    /**
     * Creates a new PomProcessor.
     *
     * @param logger the logger for reporting processing progress
     * @param downloader the dependency downloader
     * @param versionResolver the version resolver
     * @param cache the dependency cache
     * @param includeDependencyManagement whether to include dependencyManagement sections
     * @param skipOptionalDependencies whether to skip optional dependencies
     * @param skipTestDependencies whether to skip test dependencies
     * @param excludedGroupIds the set of excluded group IDs
     * @param excludedArtifactPatterns the list of excluded artifact patterns
     */
    public PomProcessor(@NotNull Logger logger,
                       @NotNull DependencyDownloader downloader,
                       @NotNull VersionResolver versionResolver,
                       @NotNull DependencyCache cache,
                       boolean includeDependencyManagement,
                       boolean skipOptionalDependencies,
                       boolean skipTestDependencies,
                       @NotNull java.util.Set<String> excludedGroupIds,
                       @NotNull List<Pattern> excludedArtifactPatterns) {
        this.logger = requireNonNull(logger, "Logger cannot be null");
        this.downloader = requireNonNull(downloader, "Downloader cannot be null");
        this.versionResolver = requireNonNull(versionResolver, "VersionResolver cannot be null");
        this.cache = requireNonNull(cache, "Cache cannot be null");
        this.includeDependencyManagement = includeDependencyManagement;
        this.skipOptionalDependencies = skipOptionalDependencies;
        this.skipTestDependencies = skipTestDependencies;
        this.excludedGroupIds = requireNonNull(excludedGroupIds, "Excluded group IDs cannot be null");
        this.excludedArtifactPatterns = requireNonNull(excludedArtifactPatterns, "Excluded artifact patterns cannot be null");
    }

    /**
     * Downloads and processes POM including parent hierarchy.
     *
     * @param dependency the dependency to process
     * @return a POM context containing processed information, or null if POM not found
     * @throws Exception if an error occurs during processing
     */
    @Nullable
    public PomContext downloadAndProcessPom(@NotNull Dependency dependency) throws Exception {
        PomInfo pomInfo = downloader.downloadAndParsePom(dependency);
        if (pomInfo == null) {
            logger.debug("No POM found for: " + dependency.toShortString());
            return null;
        }

        PomInfo mergedPomInfo = processParentHierarchy(pomInfo);

        List<Dependency> resolvedDependencies = resolveAllDependencies(mergedPomInfo);

        return new PomContext(mergedPomInfo, resolvedDependencies);
    }

    /**
     * Processes the complete parent POM hierarchy.
     *
     * @param pomInfo the child POM information
     * @return the merged POM information including parent data
     * @throws Exception if an error occurs during processing
     */
    @NotNull
    private PomInfo processParentHierarchy(@NotNull PomInfo pomInfo) throws Exception {
        List<PomInfo> pomHierarchy = new ArrayList<>();

        PomInfo currentPom = pomInfo;
        pomHierarchy.add(currentPom);

        while (currentPom.hasParent()) {
            ParentInfo parentInfo = currentPom.parentInfo();
            Dependency parentDependency = parentInfo.toDependency();
            logger.debug("Processing parent POM: " + parentDependency.toShortString());

            PomInfo parentPom = downloader.downloadAndParsePom(parentDependency);
            if (parentPom == null) {
                logger.debug("Could not download parent POM: " + parentDependency.toShortString());
                break;
            }

            pomHierarchy.add(parentPom);
            currentPom = parentPom;

            if (pomHierarchy.size() > MAX_PARENT_HIERARCHY_DEPTH) {
                logger.warn("Maximum parent hierarchy depth reached (" + MAX_PARENT_HIERARCHY_DEPTH + ") for " + pomInfo.artifactId());
                break;
            }
        }

        return mergePomHierarchy(pomHierarchy);
    }

    /**
     * Merges POM hierarchy from parents to child.
     *
     * @param pomHierarchy the list of POMs in the hierarchy (child first, then parents)
     * @return the merged POM information
     * @throws IllegalArgumentException if the hierarchy is empty
     */
    @NotNull
    private PomInfo mergePomHierarchy(@NotNull List<PomInfo> pomHierarchy) {
        if (pomHierarchy.isEmpty()) {
            throw new IllegalArgumentException("POM hierarchy cannot be empty");
        }

        if (pomHierarchy.size() == 1) {
            PomInfo singlePom = pomHierarchy.get(0);
            cache.addGlobalDependencyManagement(singlePom.dependencyManagement());
            logger.debug("Added " + singlePom.dependencyManagement().size() + " entries to global dependency management from " + singlePom.artifactId());
            return singlePom;
        }

        Map<String, String> mergedDependencyManagement = new HashMap<>();
        Map<String, String> mergedProperties = new HashMap<>();

        for (int i = pomHierarchy.size() - 1; i >= 0; i--) {
            PomInfo pom = pomHierarchy.get(i);

            Map<String, String> pomProperties = pom.properties();
            for (Map.Entry<String, String> entry : pomProperties.entrySet()) {
                mergedProperties.putIfAbsent(entry.getKey(), entry.getValue());
            }

            Map<String, String> pomDependencyManagement = pom.dependencyManagement();
            for (Map.Entry<String, String> entry : pomDependencyManagement.entrySet()) {
                if (i == 0) {
                    mergedDependencyManagement.put(entry.getKey(), entry.getValue());
                } else {
                    mergedDependencyManagement.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        }

        cache.addGlobalDependencyManagement(mergedDependencyManagement);
        logger.debug("Merged " + mergedDependencyManagement.size() + " dependency management entries from hierarchy");

        PomInfo childPom = pomHierarchy.get(0);

        return new PomInfo(
                childPom.groupId(),
                childPom.artifactId(),
                childPom.version(),
                childPom.dependencies(),
                mergedProperties,
                mergedDependencyManagement,
                childPom.parentInfo()
        );
    }

    /**
     * Resolves all dependencies from a POM with version resolution.
     *
     * @param pomInfo the POM information
     * @return a list of resolved dependencies
     */
    @NotNull
    private List<Dependency> resolveAllDependencies(@NotNull PomInfo pomInfo) {
        List<Dependency> resolved = new ArrayList<>();

        for (Dependency dependency : pomInfo.getRuntimeDependencies()) {
            try {
                if (skipOptionalDependencies && dependency.isOptional()) {
                    logger.debug("Skipping optional dependency: " + dependency.toShortString());
                    continue;
                }

                if (skipTestDependencies && "test".equals(dependency.getScope())) {
                    logger.debug("Skipping test dependency: " + dependency.toShortString());
                    continue;
                }

                if (shouldExcludeDependency(dependency)) {
                    logger.debug("Skipping excluded dependency: " + dependency.toShortString());
                    continue;
                }

                Dependency resolvedDep;
                if (dependency.getVersion() == null || dependency.getVersion().trim().isEmpty()) {
                    resolvedDep = versionResolver.resolveDependencyFromManagement(dependency, pomInfo);
                    logger.debug("Resolved version from dependencyManagement: " + resolvedDep.toShortString());
                } else {
                    resolvedDep = dependency;
                }

                if (resolvedDep.getVersion() == null || resolvedDep.getVersion().trim().isEmpty()) {
                    resolvedDep = versionResolver.resolveDependencyVersion(resolvedDep);
                }

                resolved.add(resolvedDep);
                logger.debug("Resolved transitive dependency: " + resolvedDep.toShortString());
            } catch (Exception e) {
                logger.debug("Could not resolve dependency: " + dependency.getGroupArtifactId() + " - " + e.getMessage());

                try {
                    Dependency withoutVersion = Dependency.builder()
                            .groupId(dependency.getGroupId())
                            .artifactId(dependency.getArtifactId())
                            .version("")
                            .build();
                    Dependency resolvedFromMetadata = versionResolver.resolveDependencyVersion(withoutVersion);
                    resolved.add(resolvedFromMetadata);
                    logger.debug("Resolved from metadata: " + resolvedFromMetadata.toShortString());
                } catch (Exception e2) {
                    logger.debug("Failed to resolve from metadata: " + dependency.getGroupArtifactId() + " - " + e2.getMessage());
                }
            }
        }

        if (includeDependencyManagement) {
            resolveDependenciesFromDependencyManagement(pomInfo, resolved);
        }

        return resolved;
    }

    /**
     * Extracts and resolves dependencies defined in dependencyManagement sections.
     *
     * @param pomInfo the POM information
     * @param resolvedList the list to add resolved dependencies to
     */
    private void resolveDependenciesFromDependencyManagement(@NotNull PomInfo pomInfo, @NotNull List<Dependency> resolvedList) {
        Map<String, String> dependencyManagement = new HashMap<>(pomInfo.dependencyManagement());
        dependencyManagement.putAll(cache.getGlobalDependencyManagement());

        logger.debug("Processing " + dependencyManagement.size() + " entries from dependencyManagement");

        for (Map.Entry<String, String> entry : dependencyManagement.entrySet()) {
            String dependencyKey = entry.getKey();
            String version = entry.getValue();

            try {
                boolean alreadyIncluded = resolvedList.stream()
                        .anyMatch(d -> d.getGroupArtifactId().equals(dependencyKey));

                if (alreadyIncluded) {
                    continue;
                }

                String[] parts = dependencyKey.split(":");
                if (parts.length != 2) {
                    continue;
                }

                Dependency managementDep = Dependency.builder()
                        .groupId(parts[0])
                        .artifactId(parts[1])
                        .version(version)
                        .build();

                if (shouldExcludeDependency(managementDep)) {
                    logger.debug("Skipping excluded dependency from management: " + managementDep.toShortString());
                    continue;
                }

                if (managementDep.getGroupId() != null && managementDep.getArtifactId() != null &&
                        managementDep.getVersion() != null && !managementDep.getVersion().isEmpty()) {
                    resolvedList.add(managementDep);
                    logger.debug("Added dependency from management: " + managementDep.toShortString());
                }
            } catch (Exception e) {
                logger.debug("Failed to process dependencyManagement entry: " + dependencyKey + " - " + e.getMessage());
            }
        }
    }

    /**
     * Checks if a dependency should be excluded based on configured exclusion rules.
     *
     * @param dependency the dependency to check
     * @return true if the dependency should be excluded, false otherwise
     */
    private boolean shouldExcludeDependency(Dependency dependency) {
        if (excludedGroupIds.contains(dependency.getGroupId())) {
            return true;
        }

        String coordinates = dependency.getGroupArtifactId();
        for (Pattern pattern : excludedArtifactPatterns) {
            if (pattern.matcher(coordinates).matches()) {
                return true;
            }
        }

        return false;
    }
}

