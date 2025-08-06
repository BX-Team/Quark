package org.bxteam.quark.dependency;

import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.pom.MetadataReader;
import org.bxteam.quark.pom.PomReader;
import org.bxteam.quark.repository.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Resolves Maven dependencies and their transitive dependencies.
 *
 * @author BxTeam
 * @since 1.0.0
 */
public class DependencyResolver {
    private final Logger logger;
    private final List<Repository> repositories;
    private final Path localRepository;
    private final PomReader pomReader;
    private final MetadataReader metadataReader;
    private final boolean includeDependencyManagement;

    // Dependency download limiting options
    private final int maxTransitiveDepth;
    private final Set<String> excludedGroupIds;
    private final List<Pattern> excludedArtifactPatterns;
    private final boolean skipOptionalDependencies;
    private final boolean skipTestDependencies;

    // Caches to avoid redundant operations
    private final Map<String, PomReader.PomInfo> pomCache = new ConcurrentHashMap<>();
    private final Map<String, MetadataReader.MavenMetadata> metadataCache = new ConcurrentHashMap<>();
    private final Map<String, String> resolvedVersionCache = new ConcurrentHashMap<>();
    private final Set<String> processedDependencies = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> dependencyDepths = new ConcurrentHashMap<>();

    // Global dependency management from all parent POMs
    private final Map<String, String> globalDependencyManagement = new ConcurrentHashMap<>();

    /**
     * Creates a new DependencyResolver with default settings.
     */
    public DependencyResolver(@NotNull Logger logger,
                              @NotNull List<Repository> repositories,
                              @NotNull Path localRepository) {
        this(new Builder(logger, repositories, localRepository));
    }

    /**
     * Creates a new DependencyResolver with the specified settings.
     */
    private DependencyResolver(Builder builder) {
        this.logger = requireNonNull(builder.logger, "Logger cannot be null");
        this.repositories = new ArrayList<>(requireNonNull(builder.repositories, "Repositories cannot be null"));
        this.localRepository = requireNonNull(builder.localRepository, "Local repository cannot be null");
        this.pomReader = new PomReader();
        this.metadataReader = new MetadataReader();
        this.includeDependencyManagement = builder.includeDependencyManagement;
        this.maxTransitiveDepth = builder.maxTransitiveDepth;
        this.excludedGroupIds = new HashSet<>(builder.excludedGroupIds);
        this.excludedArtifactPatterns = new ArrayList<>(builder.excludedArtifactPatterns);
        this.skipOptionalDependencies = builder.skipOptionalDependencies;
        this.skipTestDependencies = builder.skipTestDependencies;
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
        private Set<String> excludedGroupIds = new HashSet<>();
        private List<Pattern> excludedArtifactPatterns = new ArrayList<>();
        private boolean skipOptionalDependencies = true;
        private boolean skipTestDependencies = true;

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
         */
        public Builder includeDependencyManagement(boolean include) {
            this.includeDependencyManagement = include;
            return this;
        }

        /**
         * Set the maximum depth for transitive dependencies.
         * Root dependencies have depth 0.
         * Default: Integer.MAX_VALUE (no limit)
         */
        public Builder maxTransitiveDepth(int depth) {
            this.maxTransitiveDepth = Math.max(0, depth);
            return this;
        }

        /**
         * Exclude dependencies with the specified group IDs.
         */
        public Builder excludeGroupIds(String... groupIds) {
            this.excludedGroupIds.addAll(Arrays.asList(groupIds));
            return this;
        }

        /**
         * Exclude dependencies matching the specified patterns.
         * Patterns are in the format "groupId:artifactId" and support wildcards (*).
         */
        public Builder excludeArtifacts(String... patterns) {
            for (String pattern : patterns) {
                // Convert Maven-style wildcards to regex
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
         */
        public Builder skipOptionalDependencies(boolean skip) {
            this.skipOptionalDependencies = skip;
            return this;
        }

        /**
         * Skip test dependencies.
         * Default: true
         */
        public Builder skipTestDependencies(boolean skip) {
            this.skipTestDependencies = skip;
            return this;
        }

        /**
         * Build the DependencyResolver with the configured settings.
         */
        public DependencyResolver build() {
            return new DependencyResolver(this);
        }
    }

    /**
     * Resolves all transitive dependencies for the given root dependencies.
     */
    @NotNull
    public ResolutionResult resolveDependencies(@NotNull Collection<Dependency> rootDependencies) {
        requireNonNull(rootDependencies, "Root dependencies cannot be null");

        logger.info("Resolving dependencies...");

        // Clear caches for fresh resolution
        pomCache.clear();
        metadataCache.clear();
        resolvedVersionCache.clear();
        processedDependencies.clear();
        dependencyDepths.clear();
        globalDependencyManagement.clear();

        Set<Dependency> allDependencies = new LinkedHashSet<>();
        Set<Dependency> toProcess = new LinkedHashSet<>(rootDependencies);
        List<String> resolutionErrors = new ArrayList<>();

        // Initialize root dependencies with depth 0
        for (Dependency root : rootDependencies) {
            dependencyDepths.put(root.getCoordinates(), 0);
        }

        int iteration = 1;

        // Phase 1: Collect all dependencies by parsing POMs
        while (!toProcess.isEmpty()) {
            Set<Dependency> currentBatch = new LinkedHashSet<>(toProcess);
            toProcess.clear();

            logger.debug("=== Iteration " + iteration + " - Processing " + currentBatch.size() + " dependencies ===");

            for (Dependency dependency : currentBatch) {
                String dependencyKey = dependency.getCoordinates();

                if (processedDependencies.contains(dependencyKey)) {
                    continue; // Already processed
                }

                try {
                    // Skip excluded dependencies
                    if (shouldExcludeDependency(dependency)) {
                        logger.debug("Skipping excluded dependency: " + dependency.toShortString());
                        processedDependencies.add(dependencyKey);
                        continue;
                    }

                    // Get current depth of this dependency
                    int currentDepth = dependencyDepths.getOrDefault(dependencyKey, 0);

                    // Skip if exceeds max depth (but always process root dependencies)
                    if (currentDepth > 0 && currentDepth > maxTransitiveDepth) {
                        logger.debug("Skipping dependency exceeding max depth: " + dependency.toShortString());
                        processedDependencies.add(dependencyKey);
                        continue;
                    }

                    // Resolve version if needed
                    Dependency resolvedDependency = resolveDependencyVersion(dependency);
                    logger.debug("Processing: " + resolvedDependency.toShortString());

                    // Add to resolved dependencies
                    allDependencies.add(resolvedDependency);
                    processedDependencies.add(dependencyKey);

                    // Download and parse POM with full dependency resolution
                    PomContext pomContext = downloadAndProcessPom(resolvedDependency);
                    if (pomContext != null) {
                        logger.debug("Found " + pomContext.allDependencies().size() + " transitive dependencies for " + resolvedDependency.toShortString());
                        // Add transitive dependencies to next batch
                        for (Dependency transitive : pomContext.allDependencies()) {
                            String transitiveKey = transitive.getCoordinates();
                            if (!processedDependencies.contains(transitiveKey)) {
                                // Set depth for the transitive dependency
                                dependencyDepths.put(transitiveKey, currentDepth + 1);
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

            // Safety check to prevent infinite loops
            if (iteration > 50) {
                resolutionErrors.add("Maximum resolution iterations reached - possible circular dependencies");
                break;
            }
        }

        logger.info("Resolved " + allDependencies.size() + " dependencies");

        // Phase 2: Download all JAR files
        List<ResolvedDependency> resolvedDependencies = new ArrayList<>();

        for (Dependency dependency : allDependencies) {
            try {
                DownloadResult downloadResult = downloadJar(dependency);
                resolvedDependencies.add(new ResolvedDependency(dependency, downloadResult.jarPath()));

                // Log successful downloads with repository info
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
     */
    private boolean shouldExcludeDependency(Dependency dependency) {
        // Check group ID exclusions
        if (excludedGroupIds.contains(dependency.getGroupId())) {
            return true;
        }

        // Check pattern exclusions
        String coordinates = dependency.getGroupArtifactId();
        for (Pattern pattern : excludedArtifactPatterns) {
            if (pattern.matcher(coordinates).matches()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Downloads and processes POM including parent hierarchy.
     */
    @Nullable
    private PomContext downloadAndProcessPom(@NotNull Dependency dependency) throws Exception {
        // Download and parse the main POM
        PomReader.PomInfo pomInfo = downloadAndParsePom(dependency);
        if (pomInfo == null) {
            logger.debug("No POM found for: " + dependency.toShortString());
            return null; // No POM available
        }

        // Process parent POM hierarchy
        PomReader.PomInfo mergedPomInfo = processParentHierarchy(pomInfo);

        // Resolve all dependencies from the merged POM
        List<Dependency> resolvedDependencies = resolveAllDependencies(mergedPomInfo);

        return new PomContext(mergedPomInfo, resolvedDependencies);
    }

    /**
     * Processes the complete parent POM hierarchy.
     */
    @NotNull
    private PomReader.PomInfo processParentHierarchy(@NotNull PomReader.PomInfo pomInfo) throws Exception {
        List<PomReader.PomInfo> pomHierarchy = new ArrayList<>();

        // Collect all POMs in the hierarchy (child to parent)
        PomReader.PomInfo currentPom = pomInfo;
        pomHierarchy.add(currentPom);

        // Follow parent chain
        while (currentPom.hasParent()) {
            PomReader.ParentInfo parentInfo = currentPom.parentInfo();
            Dependency parentDependency = parentInfo.toDependency();
            logger.debug("Processing parent POM: " + parentDependency.toShortString());

            PomReader.PomInfo parentPom = downloadAndParsePom(parentDependency);
            if (parentPom == null) {
                logger.debug("Could not download parent POM: " + parentDependency.toShortString());
                break;
            }

            pomHierarchy.add(parentPom);
            currentPom = parentPom;

            // Safety check to prevent infinite parent loops
            if (pomHierarchy.size() > 10) {
                logger.warn("Maximum parent hierarchy depth reached (10) for " + pomInfo.artifactId());
                break;
            }
        }

        // Merge POMs from parent to child (parent dependency management has lower priority)
        return mergePomHierarchy(pomHierarchy);
    }

    /**
     * Merges POM hierarchy from parents to child.
     */
    @NotNull
    private PomReader.PomInfo mergePomHierarchy(@NotNull List<PomReader.PomInfo> pomHierarchy) {
        if (pomHierarchy.isEmpty()) {
            throw new IllegalArgumentException("POM hierarchy cannot be empty");
        }

        if (pomHierarchy.size() == 1) {
            PomReader.PomInfo singlePom = pomHierarchy.get(0);
            // Add to global dependency management
            globalDependencyManagement.putAll(singlePom.dependencyManagement());
            logger.debug("Added " + singlePom.dependencyManagement().size() + " entries to global dependency management from " + singlePom.artifactId());
            return singlePom;
        }

        // Start with the topmost parent (last in list)
        Map<String, String> mergedDependencyManagement = new HashMap<>();
        Map<String, String> mergedProperties = new HashMap<>();

        // Merge from parent to child (reverse order)
        for (int i = pomHierarchy.size() - 1; i >= 0; i--) {
            PomReader.PomInfo pom = pomHierarchy.get(i);

            // Parent properties and dependency management have lower priority (child overrides parent)
            Map<String, String> pomProperties = pom.properties();
            for (Map.Entry<String, String> entry : pomProperties.entrySet()) {
                mergedProperties.putIfAbsent(entry.getKey(), entry.getValue());
            }

            // Process dependencyManagement - child entries override parent entries
            Map<String, String> pomDependencyManagement = pom.dependencyManagement();
            for (Map.Entry<String, String> entry : pomDependencyManagement.entrySet()) {
                // In standard Maven, child entries override parent entries
                if (i == 0) {
                    mergedDependencyManagement.put(entry.getKey(), entry.getValue());
                } else {
                    mergedDependencyManagement.putIfAbsent(entry.getKey(), entry.getValue());
                }
            }
        }

        // Add to global dependency management
        globalDependencyManagement.putAll(mergedDependencyManagement);
        logger.debug("Merged " + mergedDependencyManagement.size() + " dependency management entries from hierarchy");

        // Use the child POM (first in list) as base
        PomReader.PomInfo childPom = pomHierarchy.get(0);

        // Create a merged POM info
        return new PomReader.PomInfo(
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
     */
    @NotNull
    private List<Dependency> resolveAllDependencies(@NotNull PomReader.PomInfo pomInfo) {
        List<Dependency> resolved = new ArrayList<>();

        // Process regular dependencies
        for (Dependency dependency : pomInfo.getRuntimeDependencies()) {
            try {
                // Skip optional dependencies if configured
                if (skipOptionalDependencies && dependency.isOptional()) {
                    logger.debug("Skipping optional dependency: " + dependency.toShortString());
                    continue;
                }

                // Skip test dependencies if configured
                if (skipTestDependencies && "test".equals(dependency.getScope())) {
                    logger.debug("Skipping test dependency: " + dependency.toShortString());
                    continue;
                }

                // Skip excluded dependencies
                if (shouldExcludeDependency(dependency)) {
                    logger.debug("Skipping excluded dependency: " + dependency.toShortString());
                    continue;
                }

                // Special handling for dependencies without version - must be resolved from dependencyManagement
                Dependency resolvedDep;
                if (dependency.getVersion() == null || dependency.getVersion().trim().isEmpty()) {
                    resolvedDep = resolveDependencyFromManagement(dependency, pomInfo);
                    logger.debug("Resolved version from dependencyManagement: " + resolvedDep.toShortString());
                } else {
                    resolvedDep = dependency;
                }

                if (resolvedDep.getVersion() == null || resolvedDep.getVersion().trim().isEmpty()) {
                    // If still no version, try metadata
                    resolvedDep = resolveDependencyVersion(resolvedDep);
                }

                resolved.add(resolvedDep);
                logger.debug("Resolved transitive dependency: " + resolvedDep.toShortString());
            } catch (Exception e) {
                logger.debug("Could not resolve dependency: " + dependency.getGroupArtifactId() + " - " + e.getMessage());

                // Try to resolve without version from metadata as fallback
                try {
                    Dependency withoutVersion = Dependency.builder()
                            .groupId(dependency.getGroupId())
                            .artifactId(dependency.getArtifactId())
                            .version("") // Will be resolved from metadata
                            .build();
                    Dependency resolvedFromMetadata = resolveDependencyVersion(withoutVersion);
                    resolved.add(resolvedFromMetadata);
                    logger.debug("Resolved from metadata: " + resolvedFromMetadata.toShortString());
                } catch (Exception e2) {
                    logger.debug("Failed to resolve from metadata: " + dependency.getGroupArtifactId() + " - " + e2.getMessage());
                }
            }
        }

        // Include dependencies from dependencyManagement if configured
        if (includeDependencyManagement) {
            resolveDependenciesFromDependencyManagement(pomInfo, resolved);
        }

        return resolved;
    }

    /**
     * Extracts and resolves dependencies defined in dependencyManagement sections.
     */
    private void resolveDependenciesFromDependencyManagement(@NotNull PomReader.PomInfo pomInfo, @NotNull List<Dependency> resolvedList) {
        // Process dependency management entries
        Map<String, String> dependencyManagement = new HashMap<>(pomInfo.dependencyManagement());
        // Also include global dependency management
        dependencyManagement.putAll(globalDependencyManagement);

        logger.debug("Processing " + dependencyManagement.size() + " entries from dependencyManagement");

        for (Map.Entry<String, String> entry : dependencyManagement.entrySet()) {
            String dependencyKey = entry.getKey();
            String version = entry.getValue();

            try {
                // Skip if already processed through regular dependencies
                boolean alreadyIncluded = resolvedList.stream()
                        .anyMatch(d -> d.getGroupArtifactId().equals(dependencyKey));

                if (alreadyIncluded) {
                    continue;
                }

                // Create dependency from dependencyManagement entry
                String[] parts = dependencyKey.split(":");
                if (parts.length != 2) {
                    continue; // Invalid format
                }

                Dependency managementDep = Dependency.builder()
                        .groupId(parts[0])
                        .artifactId(parts[1])
                        .version(version)
                        .build();

                // Skip excluded dependencies
                if (shouldExcludeDependency(managementDep)) {
                    logger.debug("Skipping excluded dependency from management: " + managementDep.toShortString());
                    continue;
                }

                // Validate dependency before adding
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
     * Resolves dependency version using POM dependency management and global management.
     */
    @NotNull
    private Dependency resolveDependencyFromManagement(@NotNull Dependency dependency, @NotNull PomReader.PomInfo pomInfo) {
        // If dependency already has a version, return as is
        if (dependency.getVersion() != null && !dependency.getVersion().trim().isEmpty()) {
            return dependency;
        }

        String dependencyKey = dependency.getGroupArtifactId();
        String version = null;

        // Try local POM dependency management first
        if (pomInfo.dependencyManagement() != null) {
            version = pomInfo.dependencyManagement().get(dependencyKey);
            if (version != null && !version.trim().isEmpty()) {
                logger.debug("Found version in local dependency management for " + dependencyKey + ": " + version);
            }
        }

        // Try global dependency management if local didn't have it
        if ((version == null || version.trim().isEmpty()) && !globalDependencyManagement.isEmpty()) {
            version = globalDependencyManagement.get(dependencyKey);
            if (version != null && !version.trim().isEmpty()) {
                logger.debug("Found version in global dependency management for " + dependencyKey + ": " + version);
            }
        }

        // Try cached version
        if (version == null || version.trim().isEmpty()) {
            version = resolvedVersionCache.get(dependencyKey);
            if (version != null && !version.trim().isEmpty()) {
                logger.debug("Found version in resolved version cache for " + dependencyKey + ": " + version);
            }
        }

        // If we found a version, use it
        if (version != null && !version.trim().isEmpty()) {
            logger.debug("Resolved version for " + dependencyKey + " -> " + version);
            return dependency.withVersion(version);
        }

        // Try to resolve from metadata as last resort
        logger.debug("Attempting to resolve version from metadata for " + dependencyKey);
        return resolveDependencyVersion(dependency);
    }

    /**
     * Resolves dependency version using metadata if version is missing.
     */
    @NotNull
    private Dependency resolveDependencyVersion(@NotNull Dependency dependency) {
        // If dependency already has a version, return as is
        if (dependency.getVersion() != null && !dependency.getVersion().trim().isEmpty()) {
            return dependency;
        }

        // Try to resolve version from metadata
        String versionKey = dependency.getGroupId() + ":" + dependency.getArtifactId();
        String cachedVersion = resolvedVersionCache.get(versionKey);

        if (cachedVersion != null) {
            return dependency.withVersion(cachedVersion);
        }

        try {
            MetadataReader.MavenMetadata metadata = downloadAndParseMetadata(dependency);
            if (metadata != null) {
                String bestVersion = metadata.getBestVersion();
                if (bestVersion != null) {
                    resolvedVersionCache.put(versionKey, bestVersion);
                    logger.debug("Resolved version from metadata: " + dependency.getGroupArtifactId() + " -> " + bestVersion);
                    return dependency.withVersion(bestVersion);
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to resolve version for " + dependency.getGroupArtifactId() + ": " + e.getMessage());
        }

        throw new RuntimeException("Cannot resolve version for dependency: " + dependency.getGroupArtifactId());
    }

    /**
     * Downloads and parses metadata for a dependency.
     */
    @Nullable
    private MetadataReader.MavenMetadata downloadAndParseMetadata(@NotNull Dependency dependency) throws Exception {
        String metadataKey = dependency.getGroupArtifactId();

        // Check cache first
        MetadataReader.MavenMetadata cached = metadataCache.get(metadataKey);
        if (cached != null) {
            return cached;
        }

        // Try each repository
        for (Repository repository : repositories) {
            if (repository.isLocal()) {
                continue;
            }

            try {
                URL metadataUrl = dependency.getMetadataUri(repository.getUrl()).toURL();
                logger.debug("Downloading metadata from: " + metadataUrl);

                URLConnection connection = metadataUrl.openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                connection.setRequestProperty("User-Agent", "Quark-LibraryManager/2.0");

                try (InputStream inputStream = connection.getInputStream()) {
                    MetadataReader.MavenMetadata metadata = metadataReader.readMetadata(inputStream, metadataUrl.toString());
                    metadataCache.put(metadataKey, metadata);
                    logger.debug("Successfully parsed metadata for " + metadataKey);
                    return metadata;
                }

            } catch (Exception e) {
                logger.debug("Failed to download metadata from " + repository.getUrl() + ": " + e.getMessage());
                // Continue to next repository
            }
        }

        logger.debug("No metadata found for " + metadataKey);
        return null; // No metadata found
    }

    @Nullable
    private PomReader.PomInfo downloadAndParsePom(@NotNull Dependency dependency) throws Exception {
        String pomKey = dependency.getCoordinates();

        // Check cache first
        PomReader.PomInfo cached = pomCache.get(pomKey);
        if (cached != null) {
            return cached;
        }

        // Try to download POM from repositories
        Path localPomPath = dependency.getPomPath(localRepository);

        // Check if POM already exists locally
        if (Files.exists(localPomPath) && isValidPomFile(localPomPath)) {
            logger.debug("Using cached POM: " + dependency.toShortString());
        } else {
            // Download POM from repositories
            try {
                downloadPomFile(dependency, localPomPath);
            } catch (Exception e) {
                logger.debug("Could not download POM for " + dependency.toShortString() + ": " + e.getMessage());
                return null;
            }
        }

        // Parse POM
        if (Files.exists(localPomPath)) {
            try {
                PomReader.PomInfo pomInfo = pomReader.readPom(localPomPath);
                pomCache.put(pomKey, pomInfo);
                logger.debug("Successfully parsed POM for " + dependency.toShortString());
                return pomInfo;
            } catch (Exception e) {
                logger.debug("Could not parse POM for " + dependency.toShortString() + ": " + e.getMessage());
            }
        }

        return null; // No POM available - treat as leaf dependency
    }

    private void downloadPomFile(@NotNull Dependency dependency, @NotNull Path localPomPath) throws Exception {
        List<Exception> exceptions = new ArrayList<>();

        // Create parent directories
        Files.createDirectories(localPomPath.getParent());

        // Try each repository
        List<Repository> reposToTry = new ArrayList<>(repositories);

        // Add fallback repository if specified
        if (dependency.getFallbackRepository() != null) {
            reposToTry.add(Repository.of(dependency.getFallbackRepository()));
        }

        for (Repository repository : reposToTry) {
            if (repository.isLocal()) {
                continue; // Skip local repositories for downloading
            }

            try {
                URL pomUrl = dependency.getPomUri(repository.getUrl()).toURL();
                logger.debug("Downloading POM from: " + pomUrl);

                URLConnection connection = pomUrl.openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                connection.setRequestProperty("User-Agent", "Quark-LibraryManager/2.0");

                try (InputStream inputStream = connection.getInputStream()) {
                    Files.copy(inputStream, localPomPath);
                    logger.debug("Successfully downloaded POM: " + dependency.toShortString());
                    return; // Success
                }

            } catch (Exception e) {
                exceptions.add(e);
                logger.debug("Failed to download POM from " + repository.getUrl() + ": " + e.getMessage());
            }
        }

        // All repositories failed
        Exception lastException = exceptions.isEmpty() ?
                new RuntimeException("No repositories configured") :
                exceptions.get(exceptions.size() - 1);
        throw new RuntimeException("Failed to download POM for " + dependency.toShortString(), lastException);
    }

    @NotNull
    private DownloadResult downloadJar(@NotNull Dependency dependency) throws Exception {
        Path localJarPath = dependency.getJarPath(localRepository);

        // Check if JAR already exists locally
        if (Files.exists(localJarPath) && isValidJarFile(localJarPath)) {
            return new DownloadResult(localJarPath, null); // Cached
        }

        // Download JAR from repositories
        List<Exception> exceptions = new ArrayList<>();

        // Create parent directories
        Files.createDirectories(localJarPath.getParent());

        // Try each repository
        List<Repository> reposToTry = new ArrayList<>(repositories);

        // Add fallback repository if specified
        if (dependency.getFallbackRepository() != null) {
            reposToTry.add(Repository.of(dependency.getFallbackRepository()));
        }

        for (Repository repository : reposToTry) {
            if (repository.isLocal()) {
                continue; // Skip local repositories for downloading
            }

            try {
                URL jarUrl = dependency.getJarUri(repository.getUrl()).toURL();

                URLConnection connection = jarUrl.openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(60000);
                connection.setRequestProperty("User-Agent", "Quark-LibraryManager/2.0");

                try (InputStream inputStream = connection.getInputStream()) {
                    Files.copy(inputStream, localJarPath);

                    if (isValidJarFile(localJarPath)) {
                        return new DownloadResult(localJarPath, repository.getUrl()); // Success
                    } else {
                        Files.deleteIfExists(localJarPath);
                        throw new RuntimeException("Downloaded JAR is invalid");
                    }
                }

            } catch (Exception e) {
                exceptions.add(e);
            }
        }

        // All repositories failed
        Exception lastException = exceptions.isEmpty() ?
                new RuntimeException("No repositories configured") :
                exceptions.get(exceptions.size() - 1);
        throw new RuntimeException("Failed to download JAR for " + dependency.toShortString(), lastException);
    }

    private boolean isValidPomFile(@NotNull Path pomFile) {
        try {
            if (!Files.exists(pomFile) || !Files.isRegularFile(pomFile) || Files.size(pomFile) == 0) {
                return false;
            }

            String content = Files.readString(pomFile).trim();
            return !content.isEmpty() &&
                    content.contains("<project") &&
                    !content.toLowerCase().contains("<html");
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidJarFile(@NotNull Path jarFile) {
        try {
            return Files.exists(jarFile) &&
                    Files.isRegularFile(jarFile) &&
                    Files.size(jarFile) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // Helper classes and records

    /**
     * Context for POM processing including all resolved dependencies.
     */
    private record PomContext(@NotNull PomReader.PomInfo pomInfo, @NotNull List<Dependency> allDependencies) {
        public PomContext {
            requireNonNull(pomInfo, "POM info cannot be null");
            allDependencies = List.copyOf(requireNonNull(allDependencies, "Dependencies cannot be null"));
        }
    }

    /**
     * Result of a JAR download operation.
     */
    private record DownloadResult(@NotNull Path jarPath, @Nullable String downloadedFrom) {
        public DownloadResult {
            requireNonNull(jarPath, "JAR path cannot be null");
        }
    }

    /**
     * Result of dependency resolution.
     */
    public record ResolutionResult(List<ResolvedDependency> resolvedDependencies, List<String> errors) {
        public ResolutionResult(@NotNull List<ResolvedDependency> resolvedDependencies, @NotNull List<String> errors) {
            this.resolvedDependencies = List.copyOf(resolvedDependencies);
            this.errors = List.copyOf(errors);
        }

        @Override
        @NotNull
        public List<ResolvedDependency> resolvedDependencies() {
            return resolvedDependencies;
        }

        @Override
        @NotNull
        public List<String> errors() {
            return errors;
        }

        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public int getDependencyCount() {
            return resolvedDependencies.size();
        }
    }

    /**
     * A successfully resolved dependency with its JAR path.
     */
    public record ResolvedDependency(Dependency dependency, Path jarPath) {
        public ResolvedDependency(@NotNull Dependency dependency, @NotNull Path jarPath) {
            this.dependency = requireNonNull(dependency, "Dependency cannot be null");
            this.jarPath = requireNonNull(jarPath, "JAR path cannot be null");
        }

        @Override
        @NotNull
        public Dependency dependency() {
            return dependency;
        }

        @Override
        @NotNull
        public Path jarPath() {
            return jarPath;
        }

        @Override
        public String toString() {
            return dependency.toShortString() + " -> " + jarPath;
        }
    }
}
