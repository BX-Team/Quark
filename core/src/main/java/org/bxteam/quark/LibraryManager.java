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
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Objects.requireNonNull;

/**
 * Abstract base class for runtime dependency management.
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

    protected LibraryManager(@NotNull LogAdapter logAdapter, @NotNull Path dataDirectory) {
        this(logAdapter, dataDirectory, DEFAULT_LIBS_DIRECTORY);
    }

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

    private void updateDependencyDownloader() {
        List<Repository> allRepositories = new ArrayList<>();
        allRepositories.add(localRepository);
        allRepositories.addAll(globalRepositories);

        this.dependencyDownloader = new DependencyDownloader(logger, localRepository, allRepositories);
    }

    protected abstract void addToClasspath(@NotNull Path jarPath);

    @NotNull
    protected abstract IsolatedClassLoader createIsolatedClassLoader();

    @Nullable
    protected abstract InputStream getResourceAsStream(@NotNull String resourcePath);

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
     */
    private boolean isMavenCentralUrl(@NotNull String url) {
        return url.equals(Repositories.MAVEN_CENTRAL) || url.equals("https://repo1.maven.org/maven2");
    }

    /**
     * Shows the Maven Central compliance warning.
     */
    private void showMavenCentralWarning() {
        RuntimeException stackTrace = new RuntimeException("Plugin used Maven Central for library resolution");
        logger.warn("Use of Maven Central as a CDN is against the Maven Central Terms of Service. " +
                "Use Repositories.GOOGLE_MAVEN_CENTRAL_MIRROR or LibraryManager#addGoogleMavenCentralMirror() instead.", stackTrace);
    }

    /**
     * Adds the Maven Central repository.
     */
    public void addMavenCentral() {
        addRepository(Repositories.MAVEN_CENTRAL);
    }

    /**
     * Adds the Google Maven Central mirror repository.
     */
    public void addGoogleMavenCentralMirror() {
        addRepository(Repositories.GOOGLE_MAVEN_CENTRAL_MIRROR);
    }

    /**
     * Adds the Sonatype OSS repository.
     */
    public void addSonatype() {
        addRepository(Repositories.SONATYPE);
    }

    /**
     * Adds the JitPack repository.
     */
    public void addJitPack() {
        addRepository(Repositories.JITPACK);
    }

    /**
     * Adds the current user's local Maven repository.
     */
    public void addMavenLocal() {
        addRepository(LocalRepository.mavenLocal().getUrl());
    }

    @NotNull
    public Collection<Repository> getRepositories() {
        return Collections.unmodifiableSet(globalRepositories);
    }

    public void loadDependency(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");
        loadDependencies(Collections.singletonList(dependency));
    }

    public void loadDependencies(@NotNull List<Dependency> dependencies) {
        loadDependencies(dependencies, Collections.emptyList());
    }

    public void loadDependencies(@NotNull List<Dependency> dependencies, @NotNull List<Relocation> relocations) {
        requireNonNull(dependencies, "Dependencies cannot be null");
        requireNonNull(relocations, "Relocations cannot be null");

        if (dependencies.isEmpty()) {
            return;
        }

        Instant startTime = Instant.now();

        try {
            logger.info("Resolving dependencies...");
            DependencyCollector collector = resolveTransitiveDependencies(dependencies);
            Collection<Dependency> resolvedDependencies = collector.getScannedDependencies();
            logger.info("Resolved " + resolvedDependencies.size() + " dependencies");

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
            logger.info("Resolving dependencies...");
            DependencyCollector collector = resolveTransitiveDependencies(dependencies);
            Collection<Dependency> resolvedDependencies = collector.getScannedDependencies();
            logger.info("Resolved " + resolvedDependencies.size() + " dependencies");

            List<DependencyLoadEntry> loadEntries = downloadAndRelocateDependencies(collector, relocations);

            // Load into isolated class loader
            for (DependencyLoadEntry entry : loadEntries) {
                classLoader.addPath(entry.path());
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

            Path downloadedPath = dependencyDownloader.downloadDependency(dependency);

            Path finalPath = applyRelocations(dependency, downloadedPath, relocations);

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

    private DependencyCollector resolveTransitiveDependencies(@NotNull Collection<Dependency> dependencies) {
        DependencyCollector collector = new DependencyCollector();
        collector.addScannedDependencies(dependencies);

        for (Dependency dependency : dependencies) {
            dependencyScanner.findAllChildren(collector, dependency);
        }

        return collector;
    }

    @NotNull
    public Logger getLogger() {
        return logger;
    }

    @NotNull
    public LocalRepository getLocalRepository() {
        return localRepository;
    }

    @NotNull
    public LogLevel getLogLevel() {
        return logger.getLevel();
    }

    public void setLogLevel(@NotNull LogLevel level) {
        logger.setLevel(requireNonNull(level, "Log level cannot be null"));
    }

    @NotNull
    public IsolatedClassLoader getGlobalIsolatedClassLoader() {
        return globalIsolatedClassLoader;
    }

    @Nullable
    public IsolatedClassLoader getIsolatedClassLoader(@NotNull String loaderId) {
        return isolatedClassLoaders.get(requireNonNull(loaderId, "Loader ID cannot be null"));
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

        } catch (Exception e) {
            logger.error("Error during LibraryManager shutdown", e);
        }

        logger.debug("LibraryManager shutdown complete");
    }

    public static class LibraryLoadException extends RuntimeException {
        public LibraryLoadException(String message) {
            super(message);
        }

        public LibraryLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
