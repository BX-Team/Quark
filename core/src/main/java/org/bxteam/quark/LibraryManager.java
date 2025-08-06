package org.bxteam.quark;

import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.bxteam.quark.dependency.Dependency;
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
public abstract class LibraryManager implements AutoCloseable {
    private static final String DEFAULT_LIBS_DIRECTORY = "libs";

    protected final Logger logger;
    protected final Path dataDirectory;
    protected final LocalRepository localRepository;

    protected final Set<Repository> globalRepositories = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean mavenCentralWarningShown = new AtomicBoolean(false);

    // Dependency resolution configuration
    private boolean includeDependencyManagement = false;
    private int maxTransitiveDepth = Integer.MAX_VALUE;
    private final Set<String> excludedGroupIds = ConcurrentHashMap.newKeySet();
    private final List<Pattern> excludedArtifactPatterns = Collections.synchronizedList(new ArrayList<>());
    private boolean skipOptionalDependencies = true;
    private boolean skipTestDependencies = true;

    protected volatile DependencyResolver dependencyResolver;

    private volatile RelocationHandler relocationHandler;

    protected final IsolatedClassLoader globalIsolatedClassLoader;
    protected final Map<String, IsolatedClassLoader> isolatedClassLoaders = new ConcurrentHashMap<>();

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

        this.globalIsolatedClassLoader = createIsolatedClassLoader();

        logger.debug("Initialized LibraryManager with data directory: " + dataDirectory);
    }

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
     * Creates a new isolated class loader instance.
     *
     * <p>This method must be implemented by platform-specific subclasses
     * to create class loaders appropriate for that platform.</p>
     *
     * @return a new isolated class loader instance
     */
    @NotNull
    protected abstract IsolatedClassLoader createIsolatedClassLoader();

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
     * @param depth the maximum depth (must be >= 0)
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
     *
     * <p>This method configures the dependency resolver to:
     * <ul>
     *   <li>Limit transitive dependencies to 3 levels deep</li>
     *   <li>Skip optional dependencies</li>
     *   <li>Skip test dependencies</li>
     *   <li>Exclude common logging frameworks</li>
     * </ul>
     * </p>
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
            DependencyResolver.ResolutionResult result = dependencyResolver.resolveDependencies(dependencies);

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
            logger.info("Resolving " + dependencies.size() + " dependencies for isolated class loader...");

            DependencyResolver.ResolutionResult result = dependencyResolver.resolveDependencies(dependencies);

            if (result.hasErrors()) {
                logger.warn("Dependency resolution completed with " + result.errors().size() + " errors:");
                result.errors().forEach(error -> logger.warn("  - " + error));
            }

            logger.info("Resolved " + result.getDependencyCount() + " total dependencies");

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

    @NotNull
    private List<DependencyLoadEntry> applyRelocations(@NotNull List<DependencyResolver.ResolvedDependency> resolvedDependencies,
                                                       @NotNull List<Relocation> relocations) {
        List<DependencyLoadEntry> loadEntries = new ArrayList<>();

        for (DependencyResolver.ResolvedDependency resolved : resolvedDependencies) {
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

        IsolatedClassLoader classLoader = createIsolatedClassLoader();
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

    @Override
    public void close() {
        logger.debug("Shutting down LibraryManager...");

        try {
            if (relocationHandler != null) {
                relocationHandler.close();
            }

            globalIsolatedClassLoader.close();
            isolatedClassLoaders.values().forEach(IsolatedClassLoader::close);
            isolatedClassLoaders.clear();
        } catch (Exception e) {
            logger.error("Error during LibraryManager shutdown", e);
        }

        logger.debug("LibraryManager shutdown complete");
    }

    /**
     * Represents a dependency with its loaded JAR file path.
     *
     * @param dependency the dependency information
     * @param path the local file system path to the JAR file
     */
    public record DependencyLoadEntry(@NotNull Dependency dependency, @NotNull Path path) {
        public DependencyLoadEntry {
            requireNonNull(dependency, "Dependency cannot be null");
            requireNonNull(path, "Path cannot be null");
        }
    }

    /**
     * Contains statistics about the current LibraryManager state.
     *
     * @param repositoryCount the number of configured repositories
     * @param loadedDependencyCount the number of loaded dependencies
     * @param isolatedClassLoaderCount the number of created isolated class loaders
     */
    public record LibraryManagerStats(int repositoryCount, int loadedDependencyCount, int isolatedClassLoaderCount) {
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
