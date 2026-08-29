package org.bxteam.quark.dependency;

import org.bxteam.quark.dependency.cache.DependencyCache;
import org.bxteam.quark.dependency.downloader.DependencyDownloader;
import org.bxteam.quark.dependency.model.DownloadResult;
import org.bxteam.quark.dependency.model.PomContext;
import org.bxteam.quark.dependency.model.ResolutionResult;
import org.bxteam.quark.dependency.model.ResolvedDependency;
import org.bxteam.quark.dependency.processor.PomProcessor;
import org.bxteam.quark.dependency.resolver.VersionResolver;
import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.repository.Repository;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Resolves Maven dependencies and their transitive dependencies.
 */
public class DependencyResolver {
    private static final int MAX_RESOLUTION_ITERATIONS = 50;

    private final Logger logger;
    private final DependencyCache cache;
    private final DependencyDownloader downloader;
    private final VersionResolver versionResolver;
    private final PomProcessor pomProcessor;

    private final int maxTransitiveDepth;
    private final Set<String> excludedGroupIds;
    private final List<Pattern> excludedArtifactPatterns;

    /**
     * Creates a new DependencyResolver with default settings.
     *
     * @param logger the logger for reporting resolution progress and errors
     * @param repositories the list of repositories to search for dependencies
     * @param localRepository the path to the local repository for caching dependencies
     * @throws NullPointerException if any parameter is null
     */
    public DependencyResolver(@NotNull Logger logger,
                              @NotNull List<Repository> repositories,
                              @NotNull Path localRepository) {
        this(new Builder(logger, repositories, localRepository));
    }

    /**
     * Creates a new DependencyResolver with the specified settings.
     *
     * @param builder the builder containing all resolution configuration
     * @throws NullPointerException if builder is null
     */
    private DependencyResolver(Builder builder) {
        this.logger = requireNonNull(builder.logger, "Logger cannot be null");
        this.cache = new DependencyCache();
        this.downloader = new DependencyDownloader(
                logger,
                builder.repositories,
                builder.localRepository,
                cache
        );
        this.versionResolver = new VersionResolver(logger, downloader, cache);
        this.pomProcessor = new PomProcessor(
                logger,
                downloader,
                versionResolver,
                cache,
                builder.includeDependencyManagement,
                builder.skipOptionalDependencies,
                builder.skipTestDependencies,
                builder.excludedGroupIds,
                builder.excludedArtifactPatterns
        );
        this.maxTransitiveDepth = builder.maxTransitiveDepth;
        this.excludedGroupIds = new HashSet<>(builder.excludedGroupIds);
        this.excludedArtifactPatterns = new ArrayList<>(builder.excludedArtifactPatterns);
    }

    /**
     * Builder for creating customized DependencyResolver instances.
     */
    public static class Builder {
        private final Logger logger;
        private final List<Repository> repositories;
        private final Path localRepository;
        private boolean includeDependencyManagement = false;
        private int maxTransitiveDepth = Integer.MAX_VALUE;
        private final Set<String> excludedGroupIds = new HashSet<>();
        private final List<Pattern> excludedArtifactPatterns = new ArrayList<>();
        private boolean skipOptionalDependencies = true;
        private boolean skipTestDependencies = true;

        /**
         * Creates a new Builder with required parameters.
         *
         * @param logger the logger for reporting resolution progress and errors
         * @param repositories the list of repositories to search for dependencies
         * @param localRepository the path to the local repository for caching dependencies
         * @throws NullPointerException if any parameter is null
         */
        public Builder(@NotNull Logger logger,
                       @NotNull List<Repository> repositories,
                       @NotNull Path localRepository) {
            this.logger = requireNonNull(logger, "Logger cannot be null");
            this.repositories = requireNonNull(repositories, "Repositories cannot be null");
            this.localRepository = requireNonNull(localRepository, "Local repository cannot be null");
        }

        /**
         * Include dependencies from dependencyManagement sections.
         * Default: false
         *
         * @param include whether to include dependencyManagement sections
         * @return this builder for chaining
         */
        public Builder includeDependencyManagement(boolean include) {
            this.includeDependencyManagement = include;
            return this;
        }

        /**
         * Set the maximum depth for transitive dependencies.
         * Root dependencies have depth 0.
         * Default: Integer.MAX_VALUE (no limit)
         *
         * @param depth the maximum depth of transitive dependencies to resolve
         * @return this builder for chaining
         */
        public Builder maxTransitiveDepth(int depth) {
            this.maxTransitiveDepth = Math.max(0, depth);
            return this;
        }

        /**
         * Exclude dependencies with the specified group IDs.
         *
         * @param groupIds the group IDs to exclude
         * @return this builder for chaining
         */
        public Builder excludeGroupIds(String... groupIds) {
            this.excludedGroupIds.addAll(Arrays.asList(groupIds));
            return this;
        }

        /**
         * Exclude dependencies matching the specified patterns.
         * Patterns are in the format "groupId:artifactId" and support wildcards (*).
         *
         * @param patterns the patterns to exclude
         * @return this builder for chaining
         */
        public Builder excludeArtifacts(String... patterns) {
            for (String pattern : patterns) {
                String regex = pattern
                        .replace(".", "\\.")
                        .replace("*", ".*");
                this.excludedArtifactPatterns.add(Pattern.compile(regex));
            }
            return this;
        }

        /**
         * Skip optional dependencies.
         * Default: true
         *
         * @param skip whether to skip optional dependencies
         * @return this builder for chaining
         */
        public Builder skipOptionalDependencies(boolean skip) {
            this.skipOptionalDependencies = skip;
            return this;
        }

        /**
         * Skip test dependencies.
         * Default: true
         *
         * @param skip whether to skip test dependencies
         * @return this builder for chaining
         */
        public Builder skipTestDependencies(boolean skip) {
            this.skipTestDependencies = skip;
            return this;
        }

        /**
         * Build the DependencyResolver with the configured settings.
         *
         * @return a new DependencyResolver instance
         */
        public DependencyResolver build() {
            return new DependencyResolver(this);
        }
    }

    /**
     * Resolves all transitive dependencies for the given root dependencies.
     *
     * <p>This method performs a full dependency resolution, downloading POM files,
     * processing transitive dependencies, and downloading JAR files for all
     * resolved dependencies according to the configured resolution settings.</p>
     *
     * @param rootDependencies the root dependencies to resolve
     * @return the resolution result containing all resolved dependencies and any errors
     * @throws NullPointerException if rootDependencies is null
     */
    @NotNull
    public ResolutionResult resolveDependencies(@NotNull Collection<Dependency> rootDependencies) {
        requireNonNull(rootDependencies, "Root dependencies cannot be null");

        logger.info("Resolving dependencies...");

        cache.clearAll();

        Set<Dependency> allDependencies = new LinkedHashSet<>();
        Set<Dependency> toProcess = new LinkedHashSet<>(rootDependencies);
        List<String> resolutionErrors = new ArrayList<>();

        for (Dependency root : rootDependencies) {
            cache.setDependencyDepth(root.getCoordinates(), 0);
        }

        int iteration = 1;

        while (!toProcess.isEmpty()) {
            Set<Dependency> currentBatch = new LinkedHashSet<>(toProcess);
            toProcess.clear();

            logger.debug("=== Iteration " + iteration + " - Processing " + currentBatch.size() + " dependencies ===");

            for (Dependency dependency : currentBatch) {
                String dependencyKey = dependency.getCoordinates();

                if (cache.isProcessed(dependencyKey)) {
                    continue;
                }

                try {
                    if (shouldExcludeDependency(dependency)) {
                        logger.debug("Skipping excluded dependency: " + dependency.toShortString());
                        cache.markAsProcessed(dependencyKey);
                        continue;
                    }

                    int currentDepth = cache.getDependencyDepth(dependencyKey, 0);

                    if (currentDepth > 0 && currentDepth > maxTransitiveDepth) {
                        logger.debug("Skipping dependency exceeding max depth: " + dependency.toShortString());
                        cache.markAsProcessed(dependencyKey);
                        continue;
                    }

                    Dependency resolvedDependency = versionResolver.resolveDependencyVersion(dependency);
                    logger.debug("Processing: " + resolvedDependency.toShortString());

                    allDependencies.add(resolvedDependency);
                    cache.markAsProcessed(dependencyKey);

                    PomContext pomContext = pomProcessor.downloadAndProcessPom(resolvedDependency);
                    if (pomContext != null) {
                        logger.debug("Found " + pomContext.allDependencies().size() + " transitive dependencies for " + resolvedDependency.toShortString());
                        for (Dependency transitive : pomContext.allDependencies()) {
                            String transitiveKey = transitive.getCoordinates();
                            if (!cache.isProcessed(transitiveKey)) {
                                cache.setDependencyDepth(transitiveKey, currentDepth + 1);
                                toProcess.add(transitive);
                                logger.debug("  + " + transitive.toShortString() + " (depth " + (currentDepth + 1) + ")");
                            }
                        }
                    }

                } catch (Exception e) {
                    String errorMsg = "Failed to resolve dependency " + dependency.toShortString() + ": " + e.getMessage();
                    resolutionErrors.add(errorMsg);
                    logger.debug("Resolution error for " + dependency.toShortString() + ": " + e.getMessage());
                }
            }

            iteration++;

            if (iteration > MAX_RESOLUTION_ITERATIONS) {
                resolutionErrors.add("Maximum resolution iterations reached - possible circular dependencies");
                break;
            }
        }

        logger.info("Resolved " + allDependencies.size() + " dependencies");

        List<ResolvedDependency> resolvedDependencies = new ArrayList<>();

        for (Dependency dependency : allDependencies) {
            try {
                DownloadResult downloadResult = downloader.downloadJar(dependency);
                resolvedDependencies.add(new ResolvedDependency(dependency, downloadResult.jarPath()));

                if (downloadResult.downloadedFrom() != null) {
                    logger.info("Downloaded " + dependency.toShortString() + " from " + downloadResult.downloadedFrom());
                }

            } catch (Exception e) {
                String errorMsg = "Failed to download JAR for " + dependency.toShortString() + ": " + e.getMessage();
                resolutionErrors.add(errorMsg);
                logger.debug("Download error for " + dependency.toShortString() + ": " + e.getMessage());
            }
        }

        return new ResolutionResult(resolvedDependencies, resolutionErrors);
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
