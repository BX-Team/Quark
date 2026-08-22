package org.bxteam.quark;

import lombok.Getter;
import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.dependency.model.ResolutionResult;
import org.bxteam.quark.dependency.model.ResolvedDependency;
import org.bxteam.quark.gradle.GradleMetadataLoader;
import org.bxteam.quark.logger.LogLevel;
import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.relocation.Relocation;
import org.bxteam.quark.relocation.RelocationHandler;
import org.bxteam.quark.repository.LocalRepository;
import org.bxteam.quark.repository.Repository;
import org.bxteam.quark.dependency.DependencyResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for runtime Maven dependency management.
 *
 * <p>This class provides a comprehensive API for loading Maven dependencies at runtime,
 * supporting transitive dependency resolution, package relocation, isolated class loading,
 * and multiple repository configurations. It serves as the foundation for platform-specific
 * implementations (Bukkit, Velocity, etc.).</p>
 */
public abstract class LibraryManager {
    /** Default name for the libraries directory. */
    private static final String DEFAULT_LIBS_DIRECTORY = "libs";

    /** Logger instance for this LibraryManager, used for all logging operations. */
    protected final Logger logger;

    /** Base directory where libraries and cache data are stored. */
    protected final Path dataDirectory;

    /** Local repository for caching downloaded dependencies. */
    protected final LocalRepository localRepository;

    /** Thread-safe set of globally configured Maven repositories. */
    protected final Set<Repository> globalRepositories = ConcurrentHashMap.newKeySet();

    /** Flag to ensure Maven Central warning is shown only once per instance. */
    private final AtomicBoolean mavenCentralWarningShown = new AtomicBoolean(false);

    /** Whether to include dependencies from dependencyManagement sections in POMs. */
    private boolean includeDependencyManagement = false;

    /** Maximum depth for resolving transitive dependencies (0 = root dependencies only). */
    private int maxTransitiveDepth = Integer.MAX_VALUE;

    /** Thread-safe set of group IDs to exclude from dependency resolution. */
    private final Set<String> excludedGroupIds = ConcurrentHashMap.newKeySet();

    /** Thread-safe list of regex patterns for excluding specific artifacts. */
    private final List<Pattern> excludedArtifactPatterns = Collections.synchronizedList(new ArrayList<>());

    /** Whether to skip optional dependencies during resolution. */
    private boolean skipOptionalDependencies = true;

    /** Whether to skip test-scoped dependencies during resolution. */
    private boolean skipTestDependencies = true;

    /** Dependency resolver instance, updated when configuration changes. */
    protected volatile DependencyResolver dependencyResolver;

    /** Handler for applying package relocations to dependencies. */
    private volatile RelocationHandler relocationHandler;

    /** Global isolated class loader for dependencies that should be shared but isolated from main classpath. */
    protected final IsolatedClassLoader globalIsolatedClassLoader = new IsolatedClassLoader();

    /** Map of named isolated class loaders created for specific use cases. */
    protected final Map<String, IsolatedClassLoader> isolatedClassLoaders = new ConcurrentHashMap<>();

    /** Map tracking all successfully loaded dependencies and their local file paths. */
    protected final Map<Dependency, Path> loadedDependencies = new ConcurrentHashMap<>();

    /**
     * Creates a new LibraryManager with default libraries directory name.
     *
     * @param logAdapter the log adapter for logging operations
     * @param dataDirectory the base directory for storing libraries and cache
     * @throws NullPointerException if any parameter is null
     */
    protected LibraryManager(@NotNull LogAdapter logAdapter, @NotNull Path dataDirectory) {
        this(logAdapter, dataDirectory, DEFAULT_LIBS_DIRECTORY);
    }

    /**
     * Creates a new LibraryManager with custom libraries directory name.
     *
     * @param logAdapter the log adapter for logging operations
     * @param dataDirectory the base directory for storing libraries and cache
     * @param librariesDirectoryName the name of the subdirectory for libraries
     * @throws NullPointerException if any parameter is null
     */
    protected LibraryManager(@NotNull LogAdapter logAdapter, @NotNull Path dataDirectory,
                             @NotNull String librariesDirectoryName) {
        this.logger = new Logger(requireNonNull(logAdapter, "Log adapter cannot be null"));
        this.dataDirectory = requireNonNull(dataDirectory, "Data directory cannot be null").toAbsolutePath();

        Path libsPath = this.dataDirectory.resolve(requireNonNull(librariesDirectoryName, "Libraries directory name cannot be null"));
        this.localRepository = new LocalRepository(libsPath);
        this.localRepository.ensureExists();

        updateDependencyResolver();

        logger.debug("Initialized LibraryManager with data directory: " + dataDirectory);
    }

    /**
     * Updates the dependency resolver with current configuration settings.
     * This method is called when repository or dependency resolution settings change.
     */
    private void updateDependencyResolver() {
        List<Repository> allRepositories = new ArrayList<>();
        allRepositories.add(localRepository);
        allRepositories.addAll(globalRepositories);

        this.dependencyResolver = new DependencyResolver.Builder(logger, allRepositories, localRepository.getPath())
                .includeDependencyManagement(includeDependencyManagement)
                .maxTransitiveDepth(maxTransitiveDepth)
                .skipOptionalDependencies(skipOptionalDependencies)
                .skipTestDependencies(skipTestDependencies)
                .excludeGroupIds(excludedGroupIds.toArray(new String[0]))
                .build();
    }

    /**
     * Adds a JAR file to the main application classpath.
     *
     * <p>This method must be implemented by platform-specific subclasses
     * to handle classpath modification in the appropriate way for that platform.</p>
     *
     * @param jarPath the path to the JAR file to add to the classpath
     */
    protected abstract void addToClasspath(@NotNull Path jarPath);

    /**
     * Gets a resource as an input stream from the application.
     *
     * <p>This method must be implemented by platform-specific subclasses
     * to provide access to embedded resources.</p>
     *
     * @param resourcePath the path to the resource
     * @return an input stream for the resource, or null if not found
     */
    @Nullable
    protected abstract InputStream getResourceAsStream(@NotNull String resourcePath);

    /**
     * Adds a Maven repository by URL.
     *
     * <p>The repository will be used for dependency resolution. If the URL
     * points to Maven Central, a warning will be displayed recommending
     * the use of the Google Maven Central mirror instead.</p>
     *
     * @param repositoryUrl the repository URL (must be a valid HTTP/HTTPS URL)
     * @throws NullPointerException if repositoryUrl is null
     */
    public void addRepository(@NotNull String repositoryUrl) {
        requireNonNull(repositoryUrl, "Repository URL cannot be null");

        if (isMavenCentralUrl(repositoryUrl) && mavenCentralWarningShown.compareAndSet(false, true)) {
            showMavenCentralWarning();
        }

        Repository repository = Repository.of(repositoryUrl);
        globalRepositories.add(repository);
        updateDependencyResolver();
        logger.debug("Added repository: " + repositoryUrl);
    }

    /**
     * Checks if a URL is Maven Central.
     *
     * @param url the URL to check
     * @return true if the URL is Maven Central, false otherwise
     */
    private boolean isMavenCentralUrl(@NotNull String url) {
        return url.equals(Repositories.MAVEN_CENTRAL) || url.equals("https://repo1.maven.org/maven2/");
    }

    /**
     * Shows the Maven Central compliance warning.
     * This warning is displayed when Maven Central is used directly,
     * which may violate their terms of service.
     */
    private void showMavenCentralWarning() {
        RuntimeException stackTrace = new RuntimeException("Plugin used Maven Central for library resolution");
        logger.warn("Use of Maven Central as a CDN is against the Maven Central Terms of Service. " +
                "Use Repositories.GOOGLE_MAVEN_CENTRAL_MIRROR or LibraryManager#addGoogleMavenCentralMirror() instead.", stackTrace);
    }

    /**
     * Adds the Maven Central repository.
     *
     * @deprecated Use {@link #addGoogleMavenCentralMirror()} instead to comply with Maven Central Terms of Service
     */
    @Deprecated
    public void addMavenCentral() {
        addRepository(Repositories.MAVEN_CENTRAL);
    }

    /**
     * Adds the Google Maven Central mirror repository.
     *
     * <p>This is the recommended way to access Maven Central artifacts
     * while complying with Maven Central's Terms of Service.</p>
     */
    public void addGoogleMavenCentralMirror() {
        addRepository(Repositories.GOOGLE_MAVEN_CENTRAL_MIRROR);
    }

    /**
     * Adds the Sonatype OSS repository.
     *
     * <p>This repository contains open-source snapshots and releases
     * hosted by Sonatype.</p>
     */
    public void addSonatype() {
        addRepository(Repositories.SONATYPE);
    }

    /**
     * Adds the JitPack repository.
     *
     * <p>JitPack is a novel package repository for JVM and Android projects.
     * It builds Git projects on demand and provides you with ready-to-use artifacts.</p>
     */
    public void addJitPack() {
        addRepository(Repositories.JITPACK);
    }

    /**
     * Adds the current user's local Maven repository.
     *
     * <p>This adds the local Maven repository (typically ~/.m2/repository)
     * to the list of available repositories for dependency resolution.</p>
     */
    public void addMavenLocal() {
        addRepository(LocalRepository.mavenLocal().getUrl());
    }

    /**
     * Gets all configured repositories.
     *
     * @return an immutable collection of all repositories
     */
    @NotNull
    public Collection<Repository> getRepositories() {
        return Collections.unmodifiableSet(globalRepositories);
    }

    /**
     * Sets whether to include dependencies from dependencyManagement sections.
     *
     * @param include true to include dependencyManagement dependencies, false otherwise
     * @return this library manager instance for chaining
     */
    public LibraryManager includeDependencyManagement(boolean include) {
        this.includeDependencyManagement = include;
        updateDependencyResolver();
        return this;
    }

    /**
     * Sets the maximum depth for transitive dependencies.
     * Root dependencies have depth 0.
     *
     * @param depth the maximum depth (must be &gt;= 0)
     * @return this library manager instance for chaining
     */
    public LibraryManager maxTransitiveDepth(int depth) {
        this.maxTransitiveDepth = Math.max(0, depth);
        updateDependencyResolver();
        return this;
    }

    /**
     * Excludes dependencies with the specified group IDs.
     *
     * @param groupIds the group IDs to exclude
     * @return this library manager instance for chaining
     */
    public LibraryManager excludeGroupIds(String... groupIds) {
        if (groupIds != null) {
            excludedGroupIds.addAll(Arrays.asList(groupIds));
            updateDependencyResolver();
        }
        return this;
    }

    /**
     * Excludes dependencies matching the specified patterns.
     * Patterns are in the format "groupId:artifactId" and support wildcards (*).
     *
     * @param patterns the patterns to exclude
     * @return this library manager instance for chaining
     */
    public LibraryManager excludeArtifacts(String... patterns) {
        if (patterns != null) {
            for (String pattern : patterns) {
                String regex = pattern
                        .replace(".", "\\.")
                        .replace("*", ".*");
                excludedArtifactPatterns.add(Pattern.compile(regex));
            }
            updateDependencyResolver();
        }
        return this;
    }

    /**
     * Sets whether to skip optional dependencies.
     *
     * @param skip true to skip optional dependencies, false otherwise
     * @return this library manager instance for chaining
     */
    public LibraryManager skipOptionalDependencies(boolean skip) {
        this.skipOptionalDependencies = skip;
        updateDependencyResolver();
        return this;
    }

    /**
     * Sets whether to skip test dependencies.
     *
     * @param skip true to skip test dependencies, false otherwise
     * @return this library manager instance for chaining
     */
    public LibraryManager skipTestDependencies(boolean skip) {
        this.skipTestDependencies = skip;
        updateDependencyResolver();
        return this;
    }

    /**
     * Configures dependency resolution with common-sense defaults to minimize downloads.
     * <p>
     * This method configures the dependency resolver to:
     * <ul>
     *   <li>Limit transitive dependencies to 3 levels deep</li>
     *   <li>Skip optional dependencies</li>
     *   <li>Skip test dependencies</li>
     *   <li>Exclude common logging frameworks</li>
     * </ul>
     *
     * @return this library manager instance for chaining
     */
    public LibraryManager optimizeDependencyDownloads() {
        return this
                .maxTransitiveDepth(3)
                .skipOptionalDependencies(true)
                .skipTestDependencies(true)
                .excludeGroupIds("javax.servlet");
    }

    /**
     * Loads a single dependency into the main classpath.
     *
     * <p>This method resolves the dependency and all its transitive dependencies,
     * then adds them to the main application classpath.</p>
     *
     * @param dependency the dependency to load
     * @throws NullPointerException if dependency is null
     * @throws LibraryLoadException if loading fails
     */
    public void loadDependency(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");
        loadDependencies(Collections.singletonList(dependency));
    }

    /**
     * Loads multiple dependencies into the main classpath.
     *
     * <p>This method resolves all dependencies and their transitive dependencies,
     * then adds them to the main application classpath.</p>
     *
     * @param dependencies the list of dependencies to load
     * @throws NullPointerException if dependencies is null
     * @throws LibraryLoadException if loading fails
     */
    public void loadDependencies(@NotNull List<Dependency> dependencies) {
        loadDependencies(dependencies, Collections.emptyList());
    }

    /**
     * Loads multiple dependencies into the main classpath with package relocations.
     *
     * <p>This method resolves all dependencies and their transitive dependencies,
     * applies the specified package relocations, then adds them to the main
     * application classpath.</p>
     *
     * @param dependencies the list of dependencies to load
     * @param relocations the list of package relocations to apply
     * @throws NullPointerException if any parameter is null
     * @throws LibraryLoadException if loading fails
     */
    public void loadDependencies(@NotNull List<Dependency> dependencies, @NotNull List<Relocation> relocations) {
        requireNonNull(dependencies, "Dependencies cannot be null");
        requireNonNull(relocations, "Relocations cannot be null");

        if (dependencies.isEmpty()) {
            return;
        }

        Instant startTime = Instant.now();

        try {
            ResolutionResult result = dependencyResolver.resolveDependencies(dependencies);

            if (result.hasErrors()) {
                logger.warn("Dependency resolution completed with " + result.errors().size() + " errors");
                if (logger.getLevel() == LogLevel.DEBUG) {
                    result.errors().forEach(error -> logger.debug("Resolution error: " + error));
                }
            }

            List<DependencyLoadEntry> loadEntries = applyRelocations(result.resolvedDependencies(), relocations);

            for (DependencyLoadEntry entry : loadEntries) {
                addToClasspath(entry.path());
                loadedDependencies.put(entry.dependency(), entry.path());
            }

            Duration elapsed = Duration.between(startTime, Instant.now());
            logger.info("Loaded " + loadEntries.size() + " dependencies in " + elapsed.toMillis() + " ms");
        } catch (Exception e) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            logger.error("Failed to load dependencies after " + elapsed.toMillis() + " ms: " + e.getMessage());
            throw new LibraryLoadException("Failed to load dependencies", e);
        }
    }

    /**
     * Loads multiple dependencies into an isolated class loader with package relocations.
     *
     * <p>This method resolves all dependencies and their transitive dependencies,
     * applies the specified package relocations, then loads them into the provided
     * isolated class loader instead of the main classpath.</p>
     *
     * @param classLoader the isolated class loader to load dependencies into
     * @param dependencies the list of dependencies to load
     * @param relocations the list of package relocations to apply
     * @throws NullPointerException if any parameter is null
     * @throws LibraryLoadException if loading fails
     */
    public void loadDependencies(@NotNull IsolatedClassLoader classLoader,
                                 @NotNull List<Dependency> dependencies,
                                 @NotNull List<Relocation> relocations) {
        requireNonNull(classLoader, "Class loader cannot be null");
        requireNonNull(dependencies, "Dependencies cannot be null");
        requireNonNull(relocations, "Relocations cannot be null");

        if (dependencies.isEmpty()) {
            return;
        }

        Instant startTime = Instant.now();

        try {
            ResolutionResult result = dependencyResolver.resolveDependencies(dependencies);

            if (result.hasErrors()) {
                logger.warn("Dependency resolution completed with " + result.errors().size() + " errors:");
                result.errors().forEach(error -> logger.warn("  - " + error));
            }

            List<DependencyLoadEntry> loadEntries = applyRelocations(result.resolvedDependencies(), relocations);

            for (DependencyLoadEntry entry : loadEntries) {
                classLoader.addPath(entry.path());
                loadedDependencies.put(entry.dependency(), entry.path());
            }

            Duration elapsed = Duration.between(startTime, Instant.now());
            logger.info("Loaded " + loadEntries.size() + " dependencies into isolated class loader in " + elapsed.toMillis() + " ms");
        } catch (Exception e) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            logger.error("Failed to load dependencies into isolated class loader after " + elapsed.toMillis() + " ms: " + e.getMessage());
            throw new LibraryLoadException("Failed to load dependencies", e);
        }
    }

    /**
     * Applies relocations to resolved dependencies if needed.
     *
     * @param resolvedDependencies the list of resolved dependencies
     * @param relocations the list of relocations to apply
     * @return a list of dependency load entries
     */
    @NotNull
    private List<DependencyLoadEntry> applyRelocations(@NotNull List<ResolvedDependency> resolvedDependencies,
                                                       @NotNull List<Relocation> relocations) {
        List<DependencyLoadEntry> loadEntries = new ArrayList<>();

        for (ResolvedDependency resolved : resolvedDependencies) {
            Dependency dependency = resolved.dependency();
            Path jarPath = resolved.jarPath();

            Path existingPath = loadedDependencies.get(dependency);
            if (existingPath != null) {
                logger.debug("Using cached dependency: " + dependency.toShortString());
                loadEntries.add(new DependencyLoadEntry(dependency, existingPath));
                continue;
            }

            Path finalPath = applyRelocations(dependency, jarPath, relocations);

            loadEntries.add(new DependencyLoadEntry(dependency, finalPath));
        }

        return loadEntries;
    }

    /**
     * Applies relocations to a single dependency JAR if needed.
     *
     * @param dependency the dependency
     * @param jarPath the path to the dependency JAR
     * @param relocations the list of relocations to apply
     * @return the path to the final JAR (relocated or original)
     */
    @NotNull
    private Path applyRelocations(@NotNull Dependency dependency, @NotNull Path jarPath, @NotNull List<Relocation> relocations) {
        if (relocations.isEmpty()) {
            return jarPath;
        }

        if (relocationHandler == null) {
            synchronized (this) {
                if (relocationHandler == null) {
                    relocationHandler = RelocationHandler.create(this);
                }
            }
        }

        return relocationHandler.relocateDependency(localRepository, jarPath, dependency, relocations);
    }

    /**
     * Loads a dependency by Maven coordinates string.
     *
     * @param coordinates the Maven coordinates in format "groupId:artifactId:version[:classifier]"
     * @throws NullPointerException if coordinates is null
     * @throws IllegalArgumentException if coordinates format is invalid
     * @throws LibraryLoadException if loading fails
     */
    public void loadDependency(@NotNull String coordinates) {
        requireNonNull(coordinates, "Coordinates cannot be null");
        loadDependency(Dependency.fromCoordinates(coordinates));
    }

    /**
     * Loads a dependency by individual Maven coordinates.
     *
     * @param groupId the Maven group ID
     * @param artifactId the Maven artifact ID
     * @param version the dependency version
     * @throws NullPointerException if any parameter is null
     * @throws LibraryLoadException if loading fails
     */
    public void loadDependency(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
        requireNonNull(groupId, "Group ID cannot be null");
        requireNonNull(artifactId, "Artifact ID cannot be null");
        requireNonNull(version, "Version cannot be null");

        loadDependency(Dependency.of(groupId, artifactId, version));
    }

    /**
     * Creates a new isolated class loader with a unique identifier.
     *
     * @param loaderId the unique identifier for the class loader
     * @return a new isolated class loader instance
     * @throws NullPointerException if loaderId is null
     */
    @NotNull
    public IsolatedClassLoader createNamedIsolatedClassLoader(@NotNull String loaderId) {
        requireNonNull(loaderId, "Loader ID cannot be null");

        IsolatedClassLoader classLoader = new IsolatedClassLoader();
        isolatedClassLoaders.put(loaderId, classLoader);

        logger.debug("Created isolated class loader: " + loaderId);
        return classLoader;
    }

    /**
     * Loads dependencies into a named isolated class loader.
     *
     * <p>If the class loader with the given ID doesn't exist, it will be created.</p>
     *
     * @param loaderId the unique identifier for the class loader
     * @param dependencies the list of dependencies to load
     * @param relocations the list of package relocations to apply
     * @throws NullPointerException if any parameter is null
     * @throws LibraryLoadException if loading fails
     */
    public void loadDependenciesIsolated(@NotNull String loaderId,
                                         @NotNull List<Dependency> dependencies,
                                         @NotNull List<Relocation> relocations) {
        IsolatedClassLoader classLoader = isolatedClassLoaders.get(loaderId);
        if (classLoader == null) {
            classLoader = createNamedIsolatedClassLoader(loaderId);
        }

        loadDependencies(classLoader, dependencies, relocations);
    }

    /**
     * Loads dependencies from Gradle plugin generated metadata.
     *
     * <p>This method reads the metadata files generated by the Quark Gradle plugin
     * and loads all configured dependencies with their associated repositories and
     * relocations. This provides seamless integration between build-time dependency
     * configuration and runtime dependency loading.</p>
     *
     * @throws GradleMetadataLoader.GradleMetadataException if metadata loading fails
     * @throws LibraryLoadException if dependency loading fails
     */
    public void loadFromGradle() {
        GradleMetadataLoader.ResourceProvider resourceProvider = this::getResourceAsStream;
        GradleMetadataLoader metadataLoader;

        try {
            metadataLoader = new GradleMetadataLoader(resourceProvider);
        } catch (GradleMetadataLoader.GradleMetadataException e) {
            logger.debug("No Gradle metadata found: " + e.getMessage());
            return;
        }

        if (!metadataLoader.hasDependencies()) {
            logger.debug("No dependencies found in Gradle metadata");
            return;
        }

        if (metadataLoader.hasRepositories()) {
            for (String repositoryUrl : metadataLoader.getRepositories()) {
                addRepository(repositoryUrl);
            }
            logger.debug("Added " + metadataLoader.getRepositories().size() + " repositories from Gradle metadata");
        }

        List<Dependency> dependencies = metadataLoader.getDependencies();
        List<Relocation> relocations = metadataLoader.hasRelocations()
            ? metadataLoader.getRelocations()
            : Collections.emptyList();

        String relocationInfo = relocations.isEmpty() ? "" : " with " + relocations.size() + " relocations";
        logger.info("Loading " + dependencies.size() + " dependencies from Gradle metadata" + relocationInfo);

        loadDependencies(dependencies, relocations);
    }

    /**
     * Loads dependencies from Gradle metadata into an isolated class loader.
     *
     * <p>This method reads the metadata files generated by the Quark Gradle plugin
     * and loads all configured dependencies into the specified isolated class loader
     * with their associated repositories and relocations.</p>
     *
     * @param classLoader the isolated class loader to load dependencies into
     * @throws NullPointerException if classLoader is null
     * @throws GradleMetadataLoader.GradleMetadataException if metadata loading fails
     * @throws LibraryLoadException if dependency loading fails
     */
    public void loadFromGradle(@NotNull IsolatedClassLoader classLoader) {
        requireNonNull(classLoader, "Class loader cannot be null");

        GradleMetadataLoader.ResourceProvider resourceProvider = this::getResourceAsStream;
        GradleMetadataLoader metadataLoader;

        try {
            metadataLoader = new GradleMetadataLoader(resourceProvider);
        } catch (GradleMetadataLoader.GradleMetadataException e) {
            logger.debug("No Gradle metadata found: " + e.getMessage());
            return;
        }

        if (!metadataLoader.hasDependencies()) {
            logger.debug("No dependencies found in Gradle metadata");
            return;
        }

        if (metadataLoader.hasRepositories()) {
            for (String repositoryUrl : metadataLoader.getRepositories()) {
                addRepository(repositoryUrl);
            }
            logger.debug("Added " + metadataLoader.getRepositories().size() + " repositories from Gradle metadata");
        }

        List<Dependency> dependencies = metadataLoader.getDependencies();
        List<Relocation> relocations = metadataLoader.hasRelocations()
            ? metadataLoader.getRelocations()
            : Collections.emptyList();

        String relocationInfo = relocations.isEmpty() ? "" : " with " + relocations.size() + " relocations";
        logger.info("Loading " + dependencies.size() + " dependencies from Gradle metadata into isolated class loader" + relocationInfo);

        loadDependencies(classLoader, dependencies, relocations);
    }

    /**
     * Loads dependencies from Gradle metadata into a named isolated class loader.
     *
     * <p>This method reads the metadata files generated by the Quark Gradle plugin
     * and loads all configured dependencies into the specified named isolated class loader.
     * If the class loader doesn't exist, it will be created.</p>
     *
     * @param loaderId the unique identifier for the class loader
     * @throws NullPointerException if loaderId is null
     * @throws GradleMetadataLoader.GradleMetadataException if metadata loading fails
     * @throws LibraryLoadException if dependency loading fails
     */
    public void loadFromGradleIsolated(@NotNull String loaderId) {
        requireNonNull(loaderId, "Loader ID cannot be null");

        IsolatedClassLoader classLoader = isolatedClassLoaders.get(loaderId);
        if (classLoader == null) {
            classLoader = createNamedIsolatedClassLoader(loaderId);
        }

        loadFromGradle(classLoader);
    }

    /**
     * Checks if Gradle metadata is available.
     *
     * <p>This method can be used to determine if the plugin was built with
     * the Quark Gradle plugin and contains dependency metadata.</p>
     *
     * @return true if Gradle metadata is available and contains dependencies
     */
    public boolean hasGradleMetadata() {
        try {
            GradleMetadataLoader.ResourceProvider resourceProvider = this::getResourceAsStream;
            GradleMetadataLoader metadataLoader = new GradleMetadataLoader(resourceProvider);
            return metadataLoader.hasDependencies();
        } catch (GradleMetadataLoader.GradleMetadataException e) {
            return false;
        }
    }

    /**
     * Gets the Gradle metadata loader if available.
     *
     * <p>This method provides access to the full metadata configuration
     * for advanced use cases.</p>
     *
     * @return the metadata loader, or null if no metadata is available
     */
    @Nullable
    public GradleMetadataLoader getGradleMetadata() {
        try {
            GradleMetadataLoader.ResourceProvider resourceProvider = this::getResourceAsStream;
            return new GradleMetadataLoader(resourceProvider);
        } catch (GradleMetadataLoader.GradleMetadataException e) {
            return null;
        }
    }

    /**
     * Gets the logger instance used by this LibraryManager.
     *
     * @return the logger instance
     */
    @NotNull
    public Logger getLogger() {
        return logger;
    }

    /**
     * Gets the local repository used for caching dependencies.
     *
     * @return the local repository instance
     */
    @NotNull
    public LocalRepository getLocalRepository() {
        return localRepository;
    }

    /**
     * Gets the current log level.
     *
     * @return the current log level
     */
    @NotNull
    public LogLevel getLogLevel() {
        return logger.getLevel();
    }

    /**
     * Sets the log level for this LibraryManager.
     *
     * @param level the new log level
     * @throws NullPointerException if level is null
     */
    public void setLogLevel(@NotNull LogLevel level) {
        logger.setLevel(requireNonNull(level, "Log level cannot be null"));
    }

    /**
     * Gets the global isolated class loader.
     *
     * <p>This class loader can be used for loading dependencies that should be
     * isolated from the main classpath but shared across multiple components.</p>
     *
     * @return the global isolated class loader
     */
    @NotNull
    public IsolatedClassLoader getGlobalIsolatedClassLoader() {
        return globalIsolatedClassLoader;
    }

    /**
     * Gets a named isolated class loader by its ID.
     *
     * @param loaderId the unique identifier of the class loader
     * @return the isolated class loader, or null if not found
     * @throws NullPointerException if loaderId is null
     */
    @Nullable
    public IsolatedClassLoader getIsolatedClassLoader(@NotNull String loaderId) {
        return isolatedClassLoaders.get(requireNonNull(loaderId, "Loader ID cannot be null"));
    }

    /**
     * Gets all loaded dependencies and their local file paths.
     *
     * @return an immutable map of loaded dependencies and their paths
     */
    @NotNull
    public Map<Dependency, Path> getLoadedDependencies() {
        return Collections.unmodifiableMap(loadedDependencies);
    }

    /**
     * Checks if a specific dependency has been loaded.
     *
     * @param dependency the dependency to check
     * @return true if the dependency has been loaded
     * @throws NullPointerException if dependency is null
     */
    public boolean isDependencyLoaded(@NotNull Dependency dependency) {
        return loadedDependencies.containsKey(requireNonNull(dependency, "Dependency cannot be null"));
    }

    /**
     * Gets statistics about the current LibraryManager state.
     *
     * @return statistics including repository count, loaded dependencies, and class loaders
     */
    @NotNull
    public LibraryManagerStats getStats() {
        return new LibraryManagerStats(
                globalRepositories.size(),
                loadedDependencies.size(),
                isolatedClassLoaders.size()
        );
    }

    /**
     * Represents a dependency with its loaded JAR file path.
     */
    @Getter
    public static final class DependencyLoadEntry {
        private final @NotNull Dependency dependency;
        private final @NotNull Path path;

        /**
         * Creates a new DependencyLoadEntry.
         *
         * @param dependency the dependency information
         * @param path the local file system path to the JAR file
         * @throws NullPointerException if any parameter is null
         */
        public DependencyLoadEntry(@NotNull Dependency dependency, @NotNull Path path) {
            requireNonNull(dependency, "Dependency cannot be null");
            requireNonNull(path, "Path cannot be null");
            this.dependency = dependency;
            this.path = path;
        }
    }

    /**
     * Contains statistics about the current LibraryManager state.
     */
    @Getter
    public static final class LibraryManagerStats {
        private final int repositoryCount;
        private final int loadedDependencyCount;
        private final int isolatedClassLoaderCount;

        /**
         * Creates a new LibraryManagerStats instance.
         *
         * @param repositoryCount the number of configured repositories
         * @param loadedDependencyCount the number of loaded dependencies
         * @param isolatedClassLoaderCount the number of created isolated class loaders
         */
        public LibraryManagerStats(int repositoryCount, int loadedDependencyCount, int isolatedClassLoaderCount) {
            this.repositoryCount = repositoryCount;
            this.loadedDependencyCount = loadedDependencyCount;
            this.isolatedClassLoaderCount = isolatedClassLoaderCount;
        }

        /**
         * Returns a string representation of the statistics.
         *
         * @return a string containing all statistics values
         */
        @Override
        public String toString() {
            return String.format("LibraryManagerStats{repositories=%d, loadedDependencies=%d, isolatedClassLoaders=%d}",
                repositoryCount, loadedDependencyCount, isolatedClassLoaderCount);
        }
    }

    /**
     * Exception thrown when dependency loading operations fail.
     *
     * <p>This exception indicates that one or more dependencies could not be
     * resolved, downloaded, or loaded into the classpath.</p>
     */
    public static class LibraryLoadException extends RuntimeException {
        /**
         * Creates a new LibraryLoadException with the specified message.
         *
         * @param message the detail message
         */
        public LibraryLoadException(String message) {
            super(message);
        }

        /**
         * Creates a new LibraryLoadException with the specified message and cause.
         *
         * @param message the detail message
         * @param cause the cause of this exception
         */
        public LibraryLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
