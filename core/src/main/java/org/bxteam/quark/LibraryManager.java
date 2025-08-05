package org.bxteam.quark;

import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.bxteam.quark.dependency.*;
import org.bxteam.quark.logger.LogLevel;
import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.pom.DependencyScanner;
import org.bxteam.quark.pom.PomXmlScanner;
import org.bxteam.quark.relocation.Relocation;
import org.bxteam.quark.relocation.RelocationHandler;
import org.bxteam.quark.repository.LocalRepository;
import org.bxteam.quark.repository.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for runtime dependency management and library loading.
 *
 * <p>The {@code LibraryManager} provides a comprehensive solution for managing Maven dependencies
 * at runtime, including dependency resolution, downloading, relocation, and class loading.
 * This class handles transitive dependency resolution, maintains a local repository cache,
 * and supports isolated class loading for dependency isolation.</p>
 *
 * @apiNote The LibraryManager follows the {@link AutoCloseable} pattern. Always call {@link #close()}
 * when done to properly clean up resources, including isolated class loaders and file handles.
 */
public abstract class LibraryManager implements AutoCloseable {
    private static final String DEFAULT_LIBS_DIRECTORY = "libs";

    protected final Logger logger;
    protected final Path dataDirectory;
    protected final LocalRepository localRepository;

    protected final Set<Repository> globalRepositories = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean mavenCentralWarningShown = new AtomicBoolean(false);

    protected final DependencyScanner dependencyScanner;
    protected volatile DependencyDownloader dependencyDownloader;

    private volatile RelocationHandler relocationHandler;

    protected final IsolatedClassLoader globalIsolatedClassLoader;
    protected final Map<String, IsolatedClassLoader> isolatedClassLoaders = new ConcurrentHashMap<>();

    protected final Map<Dependency, Path> loadedDependencies = new ConcurrentHashMap<>();

    /**
     * Constructs a new LibraryManager with the default libraries directory name.
     *
     * @param logAdapter the log adapter for logging operations
     * @param dataDirectory the base directory for storing libraries and cache data
     * @throws NullPointerException if logAdapter or dataDirectory is null
     */
    protected LibraryManager(@NotNull LogAdapter logAdapter, @NotNull Path dataDirectory) {
        this(logAdapter, dataDirectory, DEFAULT_LIBS_DIRECTORY);
    }

    /**
     * Constructs a new LibraryManager with a custom libraries directory name.
     *
     * <p>This constructor initializes the LibraryManager with the specified data directory
     * and libraries subdirectory. The local repository will be created within the
     * specified libraries directory.</p>
     *
     * @param logAdapter the log adapter for logging operations
     * @param dataDirectory the base directory for storing libraries and cache data
     * @param librariesDirectoryName the name of the subdirectory for storing libraries
     * @throws NullPointerException if any parameter is null
     */
    protected LibraryManager(@NotNull LogAdapter logAdapter, @NotNull Path dataDirectory,
                             @NotNull String librariesDirectoryName) {
        this.logger = new Logger(requireNonNull(logAdapter, "Log adapter cannot be null"));
        this.dataDirectory = requireNonNull(dataDirectory, "Data directory cannot be null").toAbsolutePath();

        Path libsPath = this.dataDirectory.resolve(requireNonNull(librariesDirectoryName, "Libraries directory name cannot be null"));
        this.localRepository = new LocalRepository(libsPath);
        this.localRepository.ensureExists();

        List<Repository> initialRepositories = new ArrayList<>();
        initialRepositories.add(localRepository);
        this.dependencyScanner = new PomXmlScanner(initialRepositories, localRepository);

        updateDependencyDownloader();

        this.globalIsolatedClassLoader = createIsolatedClassLoader();

        logger.debug("Initialized LibraryManager with data directory: " + dataDirectory);
    }

    /**
     * Updates the dependency downloader with the current list of repositories.
     * This method is called internally when repositories are added or modified.
     */
    private void updateDependencyDownloader() {
        List<Repository> allRepositories = new ArrayList<>();
        allRepositories.add(localRepository);
        allRepositories.addAll(globalRepositories);

        this.dependencyDownloader = new DependencyDownloader(logger, localRepository, allRepositories);
    }

    /**
     * Adds a JAR file to the application's classpath.
     * This method must be implemented by concrete subclasses to handle
     * platform-specific classpath manipulation.
     *
     * @param jarPath the path to the JAR file to add to the classpath
     */
    protected abstract void addToClasspath(@NotNull Path jarPath);

    /**
     * Creates a new isolated class loader instance.
     * This method must be implemented by concrete subclasses to provide
     * platform-specific isolated class loader implementations.
     *
     * @return a new isolated class loader instance
     */
    @NotNull
    protected abstract IsolatedClassLoader createIsolatedClassLoader();

    /**
     * Retrieves a resource as an input stream from the application context.
     * This method must be implemented by concrete subclasses to provide
     * access to application resources.
     *
     * @param resourcePath the path to the resource
     * @return an input stream for the resource, or null if not found
     */
    @Nullable
    protected abstract InputStream getResourceAsStream(@NotNull String resourcePath);

    /**
     * Adds a repository to the global repository list.
     *
     * <p>The repository will be used for dependency resolution and downloading.
     * If the repository URL is Maven Central, a warning will be logged about
     * terms of service compliance.</p>
     *
     * @param repositoryUrl the URL of the repository to add
     * @throws NullPointerException if repositoryUrl is null
     */
    public void addRepository(@NotNull String repositoryUrl) {
        requireNonNull(repositoryUrl, "Repository URL cannot be null");

        if (isMavenCentralUrl(repositoryUrl) && mavenCentralWarningShown.compareAndSet(false, true)) {
            showMavenCentralWarning();
        }

        Repository repository = Repository.of(repositoryUrl);
        globalRepositories.add(repository);
        updateDependencyDownloader();
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
     * Returns an unmodifiable collection of all configured repositories.
     *
     * @return an unmodifiable collection of repositories
     */
    @NotNull
    public Collection<Repository> getRepositories() {
        return Collections.unmodifiableSet(globalRepositories);
    }

    /**
     * Loads a single dependency and adds it to the classpath.
     *
     * <p>This method resolves transitive dependencies and downloads all required
     * JAR files before adding them to the application's classpath.</p>
     *
     * @param dependency the dependency to load
     * @throws NullPointerException if dependency is null
     * @throws LibraryLoadException if the dependency cannot be loaded
     */
    public void loadDependency(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");
        loadDependencies(Collections.singletonList(dependency));
    }

    /**
     * Loads multiple dependencies and adds them to the classpath.
     *
     * <p>This method resolves transitive dependencies for all provided dependencies
     * and downloads all required JAR files before adding them to the application's classpath.</p>
     *
     * @param dependencies the list of dependencies to load
     * @throws NullPointerException if dependencies is null
     * @throws LibraryLoadException if any dependency cannot be loaded
     */
    public void loadDependencies(@NotNull List<Dependency> dependencies) {
        loadDependencies(dependencies, Collections.emptyList());
    }

    /**
     * Loads multiple dependencies with relocations and adds them to the classpath.
     *
     * <p>This method resolves transitive dependencies, applies relocations to avoid
     * conflicts, and downloads all required JAR files before adding them to the
     * application's classpath.</p>
     *
     * @param dependencies the list of dependencies to load
     * @param relocations the list of relocations to apply
     * @throws NullPointerException if dependencies or relocations is null
     * @throws LibraryLoadException if any dependency cannot be loaded
     */
    public void loadDependencies(@NotNull List<Dependency> dependencies, @NotNull List<Relocation> relocations) {
        requireNonNull(dependencies, "Dependencies cannot be null");
        requireNonNull(relocations, "Relocations cannot be null");

        if (dependencies.isEmpty()) {
            return;
        }

        Instant startTime = Instant.now();

        try {
            logger.info("Resolving dependencies...");
            DependencyCollector collector = resolveTransitiveDependenciesIteratively(dependencies);
            Collection<Dependency> allResolvedDependencies = collector.getScannedDependencies();
            logger.info("Resolved " + allResolvedDependencies.size() + " dependencies");

            List<DependencyLoadEntry> loadEntries = downloadAndRelocateDependencies(collector, relocations);

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
     * Loads multiple dependencies with relocations into an isolated class loader.
     *
     * <p>This method resolves transitive dependencies, applies relocations to avoid
     * conflicts, and downloads all required JAR files before adding them to the
     * specified isolated class loader instead of the main application classpath.</p>
     *
     * @param classLoader the isolated class loader to load dependencies into
     * @param dependencies the list of dependencies to load
     * @param relocations the list of relocations to apply
     * @throws NullPointerException if any parameter is null
     * @throws LibraryLoadException if any dependency cannot be loaded
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
            logger.info("Resolving dependencies for isolated class loader...");
            DependencyCollector collector = resolveTransitiveDependenciesIteratively(dependencies);
            Collection<Dependency> allResolvedDependencies = collector.getScannedDependencies();
            logger.info("Resolved " + allResolvedDependencies.size() + " dependencies");

            List<DependencyLoadEntry> loadEntries = downloadAndRelocateDependencies(collector, relocations);

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
     * Resolves transitive dependencies iteratively until no new dependencies are found.
     *
     * <p>This method uses an iterative approach to resolve all transitive dependencies,
     * preventing infinite loops and handling circular dependencies gracefully.</p>
     *
     * @param rootDependencies the initial set of dependencies to resolve
     * @return a dependency collector containing all resolved dependencies
     */
    @NotNull
    private DependencyCollector resolveTransitiveDependenciesIteratively(@NotNull Collection<Dependency> rootDependencies) {
        if (dependencyScanner instanceof PomXmlScanner) {
            ((PomXmlScanner) dependencyScanner).clearCache();
        }

        DependencyCollector collector = new DependencyCollector();
        Set<Dependency> toProcess = new HashSet<>(rootDependencies);
        Set<Dependency> fullyProcessed = new HashSet<>();

        int iteration = 1;

        while (!toProcess.isEmpty()) {
            logger.debug("Dependency resolution iteration " + iteration + ": Processing " + toProcess.size() + " dependencies");

            Set<Dependency> currentBatch = new HashSet<>(toProcess);
            toProcess.clear();

            for (Dependency dependency : currentBatch) {
                if (fullyProcessed.contains(dependency)) {
                    continue;
                }

                try {
                    dependencyDownloader.downloadDependency(dependency);
                    collector.addScannedDependency(dependency);
                } catch (Exception e) {
                    logger.error("Failed to download dependency: " + dependency + " - " + e.getMessage());
                    collector.addScannedDependency(dependency);
                }
            }

            for (Dependency dependency : currentBatch) {
                if (fullyProcessed.contains(dependency)) {
                    continue;
                }

                try {
                    DependencyCollector tempCollector = new DependencyCollector();
                    dependencyScanner.findAllChildren(tempCollector, dependency);

                    for (Dependency newDep : tempCollector.getScannedDependencies()) {
                        if (!newDep.equals(dependency) && !fullyProcessed.contains(newDep) && !collector.hasScannedDependency(newDep)) {
                            toProcess.add(newDep);
                        }
                    }

                    fullyProcessed.add(dependency);
                } catch (Exception e) {
                    logger.warn("Failed to scan POM for " + dependency + ": " + e.getMessage());
                    fullyProcessed.add(dependency);
                }
            }

            iteration++;

            if (iteration > 20) {
                logger.warn("Maximum dependency resolution iterations reached. Stopping to prevent infinite loop.");
                break;
            }
        }

        return collector;
    }

    /**
     * Downloads and relocates dependencies as needed.
     *
     * @param collector the dependency collector containing resolved dependencies
     * @param relocations the list of relocations to apply
     * @return a list of dependency load entries with their final paths
     */
    @NotNull
    private List<DependencyLoadEntry> downloadAndRelocateDependencies(@NotNull DependencyCollector collector,
                                                                      @NotNull List<Relocation> relocations) {
        List<DependencyLoadEntry> loadEntries = new ArrayList<>();
        Collection<Dependency> dependencies = collector.getScannedDependencies();

        for (Dependency dependency : dependencies) {
            Path existingPath = loadedDependencies.get(dependency);
            if (existingPath != null) {
                logger.debug("Using cached dependency: " + dependency);
                loadEntries.add(new DependencyLoadEntry(dependency, existingPath));
                continue;
            }

            Path jarPath = dependency.toMavenJar(localRepository).toPath();
            if (!Files.exists(jarPath)) {
                logger.warn("JAR file not found for dependency (re-downloading): " + dependency);
                jarPath = dependencyDownloader.downloadDependency(dependency);
            }

            Path finalPath = applyRelocations(dependency, jarPath, relocations);

            loadEntries.add(new DependencyLoadEntry(dependency, finalPath));
        }

        return loadEntries;
    }

    /**
     * Applies relocations to a dependency JAR file.
     *
     * @param dependency the dependency being processed
     * @param jarPath the original path to the JAR file
     * @param relocations the list of relocations to apply
     * @return the path to the final JAR file (relocated if necessary)
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
     * Returns the logger instance used by this LibraryManager.
     *
     * @return the logger instance
     */
    @NotNull
    public Logger getLogger() {
        return logger;
    }

    /**
     * Returns the local repository instance used for caching dependencies.
     *
     * @return the local repository instance
     */
    @NotNull
    public LocalRepository getLocalRepository() {
        return localRepository;
    }

    /**
     * Returns the current log level.
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
     * @param level the new log level to set
     * @throws NullPointerException if level is null
     */
    public void setLogLevel(@NotNull LogLevel level) {
        logger.setLevel(requireNonNull(level, "Log level cannot be null"));
    }

    /**
     * Returns the global isolated class loader instance.
     *
     * <p>This class loader can be used to load classes in isolation from the
     * main application classpath.</p>
     *
     * @return the global isolated class loader
     */
    @NotNull
    public IsolatedClassLoader getGlobalIsolatedClassLoader() {
        return globalIsolatedClassLoader;
    }

    /**
     * Returns the isolated class loader with the specified ID, if it exists.
     *
     * @param loaderId the ID of the class loader to retrieve
     * @return the isolated class loader, or null if not found
     * @throws NullPointerException if loaderId is null
     */
    @Nullable
    public IsolatedClassLoader getIsolatedClassLoader(@NotNull String loaderId) {
        return isolatedClassLoaders.get(requireNonNull(loaderId, "Loader ID cannot be null"));
    }

    /**
     * Closes this LibraryManager and releases all associated resources.
     *
     * <p>This method performs cleanup of isolated class loaders, relocation handlers,
     * and other resources. It should be called when the LibraryManager is no longer needed.</p>
     */
    @Override
    public void close() {
        logger.debug("Shutting down LibraryManager...");

        try {
            if (relocationHandler != null) {
                relocationHandler.close();
            }

            globalIsolatedClassLoader.close();
            isolatedClassLoaders.values().forEach(IsolatedClassLoader::close);
        } catch (Exception e) {
            logger.error("Error during LibraryManager shutdown", e);
        }

        logger.debug("LibraryManager shutdown complete");
    }

    /**
     * Exception thrown when library loading operations fail.
     *
     * <p>This exception is thrown when dependencies cannot be resolved, downloaded,
     * or loaded into the classpath for any reason.</p>
     */
    public static class LibraryLoadException extends RuntimeException {
        /**
         * Constructs a new LibraryLoadException with the specified detail message.
         *
         * @param message the detail message
         */
        public LibraryLoadException(String message) {
            super(message);
        }

        /**
         * Constructs a new LibraryLoadException with the specified detail message and cause.
         *
         * @param message the detail message
         * @param cause the cause of this exception
         */
        public LibraryLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
