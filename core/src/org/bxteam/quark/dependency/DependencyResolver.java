package org.bxteam.quark.dependency;

import org.bxteam.quark.dependency.model.DownloadResult;
import org.bxteam.quark.dependency.model.PomContext;
import org.bxteam.quark.dependency.model.ResolutionResult;
import org.bxteam.quark.dependency.model.ResolvedDependency;
import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.pom.*;
import org.bxteam.quark.pom.model.MavenMetadata;
import org.bxteam.quark.pom.model.ParentInfo;
import org.bxteam.quark.pom.model.PomInfo;
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
 */
public class DependencyResolver {
    private final Logger logger;
    private final List<Repository> repositories;
    private final Path localRepository;
    private final PomReader pomReader;
    private final MetadataReader metadataReader;
    private final boolean includeDependencyManagement;

    private final int maxTransitiveDepth;
    private final Set<String> excludedGroupIds;
    private final List<Pattern> excludedArtifactPatterns;
    private final boolean skipOptionalDependencies;
    private final boolean skipTestDependencies;

    private final Map<String, PomInfo> pomCache = new ConcurrentHashMap<>();
    private final Map<String, MavenMetadata> metadataCache = new ConcurrentHashMap<>();
    private final Map<String, String> resolvedVersionCache = new ConcurrentHashMap<>();
    private final Set<String> processedDependencies = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> dependencyDepths = new ConcurrentHashMap<>();

    private final Map<String, String> globalDependencyManagement = new ConcurrentHashMap<>();

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

        pomCache.clear();
        metadataCache.clear();
        resolvedVersionCache.clear();
        processedDependencies.clear();
        dependencyDepths.clear();
        globalDependencyManagement.clear();

        Set<Dependency> allDependencies = new LinkedHashSet<>();
        Set<Dependency> toProcess = new LinkedHashSet<>(rootDependencies);
        List<String> resolutionErrors = new ArrayList<>();

        for (Dependency root : rootDependencies) {
            dependencyDepths.put(root.getCoordinates(), 0);
        }

        int iteration = 1;

        while (!toProcess.isEmpty()) {
            Set<Dependency> currentBatch = new LinkedHashSet<>(toProcess);
            toProcess.clear();

            logger.debug("=== Iteration " + iteration + " - Processing " + currentBatch.size() + " dependencies ===");

            for (Dependency dependency : currentBatch) {
                String dependencyKey = dependency.getCoordinates();

                if (processedDependencies.contains(dependencyKey)) {
                    continue;
                }

                try {
                    if (shouldExcludeDependency(dependency)) {
                        logger.debug("Skipping excluded dependency: " + dependency.toShortString());
                        processedDependencies.add(dependencyKey);
                        continue;
                    }

                    int currentDepth = dependencyDepths.getOrDefault(dependencyKey, 0);

                    if (currentDepth > 0 && currentDepth > maxTransitiveDepth) {
                        logger.debug("Skipping dependency exceeding max depth: " + dependency.toShortString());
                        processedDependencies.add(dependencyKey);
                        continue;
                    }

                    Dependency resolvedDependency = resolveDependencyVersion(dependency);
                    logger.debug("Processing: " + resolvedDependency.toShortString());

                    allDependencies.add(resolvedDependency);
                    processedDependencies.add(dependencyKey);

                    PomContext pomContext = downloadAndProcessPom(resolvedDependency);
                    if (pomContext != null) {
                        logger.debug("Found " + pomContext.allDependencies().size() + " transitive dependencies for " + resolvedDependency.toShortString());
                        for (Dependency transitive : pomContext.allDependencies()) {
                            String transitiveKey = transitive.getCoordinates();
                            if (!processedDependencies.contains(transitiveKey)) {
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

            if (iteration > 50) {
                resolutionErrors.add("Maximum resolution iterations reached - possible circular dependencies");
                break;
            }
        }

        logger.info("Resolved " + allDependencies.size() + " dependencies");

        List<ResolvedDependency> resolvedDependencies = new ArrayList<>();

        for (Dependency dependency : allDependencies) {
            try {
                DownloadResult downloadResult = downloadJar(dependency);
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

    /**
     * Downloads and processes POM including parent hierarchy.
     *
     * @param dependency the dependency to process
     * @return a POM context containing processed information, or null if POM not found
     * @throws Exception if an error occurs during processing
     */
    @Nullable
    private PomContext downloadAndProcessPom(@NotNull Dependency dependency) throws Exception {
        PomInfo pomInfo = downloadAndParsePom(dependency);
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

            PomInfo parentPom = downloadAndParsePom(parentDependency);
            if (parentPom == null) {
                logger.debug("Could not download parent POM: " + parentDependency.toShortString());
                break;
            }

            pomHierarchy.add(parentPom);
            currentPom = parentPom;

            if (pomHierarchy.size() > 10) {
                logger.warn("Maximum parent hierarchy depth reached (10) for " + pomInfo.artifactId());
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
            globalDependencyManagement.putAll(singlePom.dependencyManagement());
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

        globalDependencyManagement.putAll(mergedDependencyManagement);
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
                    resolvedDep = resolveDependencyFromManagement(dependency, pomInfo);
                    logger.debug("Resolved version from dependencyManagement: " + resolvedDep.toShortString());
                } else {
                    resolvedDep = dependency;
                }

                if (resolvedDep.getVersion() == null || resolvedDep.getVersion().trim().isEmpty()) {
                    resolvedDep = resolveDependencyVersion(resolvedDep);
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
                    Dependency resolvedFromMetadata = resolveDependencyVersion(withoutVersion);
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
        dependencyManagement.putAll(globalDependencyManagement);

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
     * Resolves dependency version using POM dependency management and global management.
     *
     * @param dependency the dependency to resolve
     * @param pomInfo the POM information
     * @return the dependency with resolved version
     */
    @NotNull
    private Dependency resolveDependencyFromManagement(@NotNull Dependency dependency, @NotNull PomInfo pomInfo) {
        if (dependency.getVersion() != null && !dependency.getVersion().trim().isEmpty()) {
            return dependency;
        }

        String dependencyKey = dependency.getGroupArtifactId();
        String version = null;

        if (pomInfo.dependencyManagement() != null) {
            version = pomInfo.dependencyManagement().get(dependencyKey);
            if (version != null && !version.trim().isEmpty()) {
                logger.debug("Found version in local dependency management for " + dependencyKey + ": " + version);
            }
        }

        if ((version == null || version.trim().isEmpty()) && !globalDependencyManagement.isEmpty()) {
            version = globalDependencyManagement.get(dependencyKey);
            if (version != null && !version.trim().isEmpty()) {
                logger.debug("Found version in global dependency management for " + dependencyKey + ": " + version);
            }
        }

        if (version == null || version.trim().isEmpty()) {
            version = resolvedVersionCache.get(dependencyKey);
            if (version != null && !version.trim().isEmpty()) {
                logger.debug("Found version in resolved version cache for " + dependencyKey + ": " + version);
            }
        }

        if (version != null && !version.trim().isEmpty()) {
            logger.debug("Resolved version for " + dependencyKey + " -> " + version);
            return dependency.withVersion(version);
        }

        logger.debug("Attempting to resolve version from metadata for " + dependencyKey);
        return resolveDependencyVersion(dependency);
    }

    /**
     * Resolves dependency version using metadata if version is missing.
     *
     * @param dependency the dependency to resolve
     * @return the dependency with resolved version
     * @throws RuntimeException if version cannot be resolved
     */
    @NotNull
    private Dependency resolveDependencyVersion(@NotNull Dependency dependency) {
        if (dependency.getVersion() != null && !dependency.getVersion().trim().isEmpty()) {
            return dependency;
        }

        String versionKey = dependency.getGroupId() + ":" + dependency.getArtifactId();
        String cachedVersion = resolvedVersionCache.get(versionKey);

        if (cachedVersion != null) {
            return dependency.withVersion(cachedVersion);
        }

        try {
            MavenMetadata metadata = downloadAndParseMetadata(dependency);
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
     *
     * @param dependency the dependency to get metadata for
     * @return the parsed Maven metadata, or null if not found
     * @throws Exception if an error occurs during download or parsing
     */
    @Nullable
    private MavenMetadata downloadAndParseMetadata(@NotNull Dependency dependency) throws Exception {
        String metadataKey = dependency.getGroupArtifactId();

        MavenMetadata cached = metadataCache.get(metadataKey);
        if (cached != null) {
            return cached;
        }

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
                    MavenMetadata metadata = metadataReader.readMetadata(inputStream, metadataUrl.toString());
                    metadataCache.put(metadataKey, metadata);
                    logger.debug("Successfully parsed metadata for " + metadataKey);
                    return metadata;
                }

            } catch (Exception e) {
                logger.debug("Failed to download metadata from " + repository.getUrl() + ": " + e.getMessage());
            }
        }

        logger.debug("No metadata found for " + metadataKey);
        return null;
    }

    /**
     * Downloads and parses a POM file for a dependency.
     *
     * @param dependency the dependency to process
     * @return the parsed POM information, or null if not found
     * @throws Exception if an error occurs during download or parsing
     */
    @Nullable
    private PomInfo downloadAndParsePom(@NotNull Dependency dependency) throws Exception {
        String pomKey = dependency.getCoordinates();

        PomInfo cached = pomCache.get(pomKey);
        if (cached != null) {
            return cached;
        }

        Path localPomPath = dependency.getPomPath(localRepository);

        if (Files.exists(localPomPath) && isValidPomFile(localPomPath)) {
            logger.debug("Using cached POM: " + dependency.toShortString());
        } else {
            try {
                downloadPomFile(dependency, localPomPath);
            } catch (Exception e) {
                logger.debug("Could not download POM for " + dependency.toShortString() + ": " + e.getMessage());
                return null;
            }
        }

        if (Files.exists(localPomPath)) {
            try {
                PomInfo pomInfo = pomReader.readPom(localPomPath);
                pomCache.put(pomKey, pomInfo);
                logger.debug("Successfully parsed POM for " + dependency.toShortString());
                return pomInfo;
            } catch (Exception e) {
                logger.debug("Could not parse POM for " + dependency.toShortString() + ": " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Downloads a POM file from repositories.
     *
     * @param dependency the dependency to download POM for
     * @param localPomPath the local path to save the POM to
     * @throws Exception if the download fails
     */
    private void downloadPomFile(@NotNull Dependency dependency, @NotNull Path localPomPath) throws Exception {
        List<Exception> exceptions = new ArrayList<>();

        Files.createDirectories(localPomPath.getParent());

        List<Repository> reposToTry = new ArrayList<>(repositories);

        if (dependency.getFallbackRepository() != null) {
            reposToTry.add(Repository.of(dependency.getFallbackRepository()));
        }

        for (Repository repository : reposToTry) {
            if (repository.isLocal()) {
                continue;
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
                    return;
                }

            } catch (Exception e) {
                exceptions.add(e);
                logger.debug("Failed to download POM from " + repository.getUrl() + ": " + e.getMessage());
            }
        }

        Exception lastException = exceptions.isEmpty() ?
                new RuntimeException("No repositories configured") :
                exceptions.get(exceptions.size() - 1);
        throw new RuntimeException("Failed to download POM for " + dependency.toShortString(), lastException);
    }

    /**
     * Downloads a JAR file from repositories.
     *
     * @param dependency the dependency to download JAR for
     * @return the download result containing the JAR path and source repository
     * @throws Exception if the download fails
     */
    @NotNull
    private DownloadResult downloadJar(@NotNull Dependency dependency) throws Exception {
        Path localJarPath = dependency.getJarPath(localRepository);

        if (Files.exists(localJarPath) && isValidJarFile(localJarPath)) {
            return new DownloadResult(localJarPath, null);
        }

        List<Exception> exceptions = new ArrayList<>();

        Files.createDirectories(localJarPath.getParent());

        List<Repository> reposToTry = new ArrayList<>(repositories);

        if (dependency.getFallbackRepository() != null) {
            reposToTry.add(Repository.of(dependency.getFallbackRepository()));
        }

        for (Repository repository : reposToTry) {
            if (repository.isLocal()) {
                continue;
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
                        return new DownloadResult(localJarPath, repository.getUrl());
                    } else {
                        Files.deleteIfExists(localJarPath);
                        throw new RuntimeException("Downloaded JAR is invalid");
                    }
                }

            } catch (Exception e) {
                exceptions.add(e);
            }
        }

        Exception lastException = exceptions.isEmpty() ?
                new RuntimeException("No repositories configured") :
                exceptions.get(exceptions.size() - 1);
        throw new RuntimeException("Failed to download JAR for " + dependency.toShortString(), lastException);
    }

    /**
     * Validates if a file is a valid POM file.
     *
     * @param pomFile the POM file path to check
     * @return true if the file is a valid POM, false otherwise
     */
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

    /**
     * Validates if a file is a valid JAR file.
     *
     * @param jarFile the JAR file path to check
     * @return true if the file is a valid JAR, false otherwise
     */
    private boolean isValidJarFile(@NotNull Path jarFile) {
        try {
            return Files.exists(jarFile) &&
                    Files.isRegularFile(jarFile) &&
                    Files.size(jarFile) > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
