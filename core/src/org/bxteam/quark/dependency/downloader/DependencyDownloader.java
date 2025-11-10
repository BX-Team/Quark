package org.bxteam.quark.dependency.downloader;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.dependency.cache.DependencyCache;
import org.bxteam.quark.dependency.model.DownloadResult;
import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.pom.MetadataReader;
import org.bxteam.quark.pom.PomReader;
import org.bxteam.quark.pom.model.MavenMetadata;
import org.bxteam.quark.pom.model.PomInfo;
import org.bxteam.quark.repository.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Handles downloading of JAR files, POM files, and Maven metadata from repositories.
 */
public class DependencyDownloader {
    private static final String USER_AGENT = "Quark-LibraryManager/2.0";
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 60000;

    private final Logger logger;
    private final List<Repository> repositories;
    private final Path localRepository;
    private final PomReader pomReader;
    private final MetadataReader metadataReader;
    private final DependencyCache cache;

    /**
     * Creates a new DependencyDownloader.
     *
     * @param logger the logger for reporting download progress
     * @param repositories the list of repositories to download from
     * @param localRepository the path to the local repository
     * @param cache the dependency cache
     */
    public DependencyDownloader(@NotNull Logger logger,
                                @NotNull List<Repository> repositories,
                                @NotNull Path localRepository,
                                @NotNull DependencyCache cache) {
        this.logger = requireNonNull(logger, "Logger cannot be null");
        this.repositories = new ArrayList<>(requireNonNull(repositories, "Repositories cannot be null"));
        this.localRepository = requireNonNull(localRepository, "Local repository cannot be null");
        this.cache = requireNonNull(cache, "Cache cannot be null");
        this.pomReader = new PomReader();
        this.metadataReader = new MetadataReader();
    }

    /**
     * Downloads and parses a POM file for a dependency.
     *
     * @param dependency the dependency to process
     * @return the parsed POM information, or null if not found
     * @throws Exception if an error occurs during download or parsing
     */
    @Nullable
    public PomInfo downloadAndParsePom(@NotNull Dependency dependency) throws Exception {
        String pomKey = dependency.getCoordinates();

        PomInfo cached = cache.getPom(pomKey);
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
                cache.cachePom(pomKey, pomInfo);
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
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("User-Agent", USER_AGENT);

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
    public DownloadResult downloadJar(@NotNull Dependency dependency) throws Exception {
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
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("User-Agent", USER_AGENT);

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
     * Downloads and parses metadata for a dependency.
     *
     * @param dependency the dependency to get metadata for
     * @return the parsed Maven metadata, or null if not found
     * @throws Exception if an error occurs during download or parsing
     */
    @Nullable
    public MavenMetadata downloadAndParseMetadata(@NotNull Dependency dependency) throws Exception {
        String metadataKey = dependency.getGroupArtifactId();

        MavenMetadata cached = cache.getMetadata(metadataKey);
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
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setRequestProperty("User-Agent", USER_AGENT);

                try (InputStream inputStream = connection.getInputStream()) {
                    MavenMetadata metadata = metadataReader.readMetadata(inputStream, metadataUrl.toString());
                    cache.cacheMetadata(metadataKey, metadata);
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

