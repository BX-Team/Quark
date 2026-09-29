package org.bxteam.quark.dependency;

import org.bxteam.quark.dependency.cache.DependencyCache;
import org.bxteam.quark.dependency.downloader.DependencyDownloader;
import org.bxteam.quark.dependency.model.DownloadResult;
import org.bxteam.quark.dependency.model.PomContext;
import org.bxteam.quark.dependency.model.ResolutionResult;
import org.bxteam.quark.dependency.model.ResolvedDependency;
import org.bxteam.quark.dependency.processor.PomProcessor;
import org.bxteam.quark.dependency.resolver.MavenVersions;
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
    private static final int MAX_MEDIATION_PASSES = 10;

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
     * <p>Each library ({@code group:artifact[:classifier]}) is resolved to one version: the version requested by
     * the roots if the library is one of them, otherwise the newest version any library in the graph asks for
     * (see {@link MavenVersions}). The graph is walked again with the chosen versions until the choice is stable,
     * so dependencies only an older, replaced version needed are dropped.</p>
     *
     * @param rootDependencies the root dependencies to resolve
     * @return the resolution result containing all resolved dependencies and any errors
     * @throws NullPointerException if rootDependencies is null
     */
    @NotNull
    public ResolutionResult resolveDependencies(@NotNull Collection<Dependency> rootDependencies) {
        return resolveDependencies(rootDependencies, true);
    }

    /**
     * Resolves the given dependencies, with or without their transitive dependencies.
     *
     * <p>Without transitive resolution exactly the given dependencies are downloaded, which is right for lists
     * that are already complete, such as the manifest of the Quark Gradle plugin: Gradle resolved the graph and
     * chose the versions when the plugin was built.</p>
     *
     * @param rootDependencies the dependencies to resolve
     * @param transitive whether the dependencies of the given dependencies are resolved too
     * @return the resolution result containing all resolved dependencies and any errors
     * @throws NullPointerException if rootDependencies is null
     */
    @NotNull
    public ResolutionResult resolveDependencies(@NotNull Collection<Dependency> rootDependencies, boolean transitive) {
        requireNonNull(rootDependencies, "Root dependencies cannot be null");

        logger.info("Resolving dependencies...");

        cache.clearAll();

        List<String> resolutionErrors = new ArrayList<>();
        Map<String, Dependency> roots = resolveRoots(rootDependencies, resolutionErrors);
        Collection<Dependency> allDependencies = transitive
                ? resolveGraph(roots, resolutionErrors)
                : roots.values();
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
     * Resolves the versions of the root dependencies. A library listed twice keeps its newest version.
     *
     * @return the roots by library key, in the given order
     */
    private Map<String, Dependency> resolveRoots(Collection<Dependency> rootDependencies, List<String> resolutionErrors) {
        Map<String, Dependency> roots = new LinkedHashMap<>();
        for (Dependency root : rootDependencies) {
            if (shouldExcludeDependency(root)) {
                logger.debug("Skipping excluded dependency: " + root.toShortString());
                continue;
            }
            try {
                Dependency resolved = versionResolver.resolveDependencyVersion(root);
                roots.merge(libraryKey(resolved), resolved, DependencyResolver::newer);
            } catch (Exception e) {
                resolutionErrors.add("Failed to resolve dependency " + root.toShortString() + ": " + e.getMessage());
                logger.debug("Resolution error for " + root.toShortString() + ": " + e.getMessage());
            }
        }
        return roots;
    }

    /**
     * Walks the graph from the roots until the chosen version of every library is stable.
     *
     * @return the chosen dependencies, roots first, then in breadth-first order
     */
    private Collection<Dependency> resolveGraph(Map<String, Dependency> roots, List<String> resolutionErrors) {
        Map<String, Dependency> selected = new LinkedHashMap<>(roots);
        Walk walk = null;

        for (int pass = 1; pass <= MAX_MEDIATION_PASSES; pass++) {
            Walk current = walk(roots, selected, resolutionErrors);
            walk = current;
            Map<String, Dependency> next = new LinkedHashMap<>();
            current.order.forEach(dependency -> next.put(libraryKey(dependency), current.requested.get(libraryKey(dependency))));
            if (coordinates(next).equals(coordinates(selected))) {
                break;
            }
            if (pass == MAX_MEDIATION_PASSES) {
                resolutionErrors.add("Dependency versions did not settle after " + MAX_MEDIATION_PASSES + " passes, using the last choice");
            }
            selected = next;
        }

        Walk last = walk;
        last.conflicts.forEach((key, versions) -> {
            if (versions.size() > 1) {
                Dependency chosen = last.requested.get(key);
                logger.debug("Version conflict for " + key + ": requested " + versions + ", using " + chosen.getVersion()
                        + (roots.containsKey(key) ? " (declared directly)" : ""));
            }
        });
        return last.order;
    }

    /**
     * One walk over the graph, visiting every library once with its selected version (or, for libraries seen
     * for the first time, the newest version requested so far).
     */
    private Walk walk(Map<String, Dependency> roots, Map<String, Dependency> selected, List<String> resolutionErrors) {
        Walk walk = new Walk();
        walk.requested.putAll(roots);
        roots.forEach((key, root) -> walk.conflicts.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(root.getVersion()));

        Deque<Dependency> queue = new ArrayDeque<>(roots.values());
        Map<String, Integer> depths = new HashMap<>();
        roots.keySet().forEach(key -> depths.put(key, 0));
        Set<String> visited = new HashSet<>();
        int processed = 0;

        while (!queue.isEmpty()) {
            Dependency requested = queue.poll();
            String key = libraryKey(requested);
            if (!visited.add(key)) {
                continue;
            }
            if (++processed > MAX_RESOLUTION_ITERATIONS * 100) {
                resolutionErrors.add("Too many dependencies - possible circular dependencies");
                break;
            }

            Dependency dependency = selected.getOrDefault(key, walk.requested.getOrDefault(key, requested));
            walk.order.add(dependency);
            logger.debug("Processing: " + dependency.toShortString());

            int depth = depths.getOrDefault(key, 0);
            if (depth >= maxTransitiveDepth) {
                continue;
            }

            PomContext pomContext;
            try {
                pomContext = pomProcessor.downloadAndProcessPom(dependency);
            } catch (Exception e) {
                resolutionErrors.add("Failed to resolve dependency " + dependency.toShortString() + ": " + e.getMessage());
                logger.debug("Resolution error for " + dependency.toShortString() + ": " + e.getMessage());
                continue;
            }
            if (pomContext == null) {
                continue;
            }

            for (Dependency transitive : pomContext.allDependencies()) {
                if (shouldExcludeDependency(transitive)) {
                    continue;
                }
                Dependency resolved;
                try {
                    resolved = versionResolver.resolveDependencyVersion(transitive);
                } catch (Exception e) {
                    resolutionErrors.add("Failed to resolve dependency " + transitive.toShortString() + ": " + e.getMessage());
                    continue;
                }
                String transitiveKey = libraryKey(resolved);
                walk.conflicts.computeIfAbsent(transitiveKey, ignored -> new LinkedHashSet<>()).add(resolved.getVersion());
                if (!roots.containsKey(transitiveKey)) {
                    walk.requested.merge(transitiveKey, resolved, DependencyResolver::newer);
                }
                depths.putIfAbsent(transitiveKey, depth + 1);
                if (!visited.contains(transitiveKey)) {
                    queue.add(resolved);
                    logger.debug("  + " + resolved.toShortString() + " (depth " + (depth + 1) + ")");
                }
            }
        }
        return walk;
    }

    /**
     * State of one {@link #walk}.
     */
    private static final class Walk {
        /** Visited dependencies with the version used. */
        final List<Dependency> order = new ArrayList<>();
        /** The version each library would get: the root version, else the newest requested. */
        final Map<String, Dependency> requested = new LinkedHashMap<>();
        /** Every version requested per library, for the conflict report. */
        final Map<String, Set<String>> conflicts = new LinkedHashMap<>();
    }

    private static Dependency newer(Dependency current, Dependency candidate) {
        return MavenVersions.compare(candidate.getVersion(), current.getVersion()) > 0 ? candidate : current;
    }

    private static Map<String, String> coordinates(Map<String, Dependency> dependencies) {
        Map<String, String> result = new HashMap<>();
        dependencies.forEach((key, dependency) -> result.put(key, dependency.getVersion()));
        return result;
    }

    /**
     * @return {@code group:artifact[:classifier]}, the identity of a library regardless of its version
     */
    private static String libraryKey(Dependency dependency) {
        return dependency.getClassifier() != null
                ? dependency.getGroupArtifactId() + ":" + dependency.getClassifier()
                : dependency.getGroupArtifactId();
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
