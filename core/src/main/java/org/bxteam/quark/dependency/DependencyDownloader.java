package org.bxteam.quark.dependency;

import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.repository.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Downloads dependency JARs and POM files from Maven repositories.
 *
 * <p>This class handles the downloading and caching of Maven dependencies
 * from remote repositories. It validates downloaded files and manages
 * the local repository cache.</p>
 */
public class DependencyDownloader {
    private final Logger logger;
    private final Repository localRepository;
    private final List<Repository> repositories;

    /**
     * Creates a new dependency downloader.
     *
     * @param logger the logger instance
     * @param localRepository the local repository for caching
     * @param repositories the list of remote repositories
     * @throws NullPointerException if any parameter is null
     */
    public DependencyDownloader(@NotNull Logger logger, @NotNull Repository localRepository, @NotNull List<Repository> repositories) {
        this.logger = requireNonNull(logger, "Logger cannot be null");
        this.localRepository = requireNonNull(localRepository, "Local repository cannot be null");
        this.repositories = new ArrayList<>(requireNonNull(repositories, "Repositories cannot be null"));
    }

    /**
     * Downloads a dependency and returns the path to the JAR file.
     *
     * @param dependency the dependency to download
     * @return the path to the downloaded JAR file
     * @throws NullPointerException if dependency is null
     * @throws DependencyException if download fails
     */
    @NotNull
    public Path downloadDependency(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");

        try {
            return tryDownloadDependency(dependency);
        } catch (URISyntaxException e) {
            throw new DependencyException("Invalid dependency coordinates: " + dependency, e);
        }
    }

    /**
     * Attempts to download a dependency from available repositories.
     *
     * @param dependency the dependency to download
     * @return the path to the JAR file
     * @throws URISyntaxException if dependency coordinates are invalid
     * @throws DependencyException if download fails from all repositories
     */
    @NotNull
    private Path tryDownloadDependency(@NotNull Dependency dependency) throws URISyntaxException {
        Path localJarPath = dependency.toMavenJar(localRepository).toPath();
        Path localPomPath = dependency.toPomXml(localRepository).toPath();

        boolean jarExists = Files.exists(localJarPath) && isValidJarFile(localJarPath);
        boolean pomExists = Files.exists(localPomPath) && isValidPomFile(localPomPath);

        if (jarExists && pomExists) {
            logger.debug("Using cached dependency: " + dependency);
            return localJarPath;
        } else if (jarExists) {
            logger.debug("Using cached JAR (POM missing): " + dependency);
            tryDownloadPomOnly(dependency, localPomPath);
            return localJarPath;
        }

        List<DependencyException> exceptions = new ArrayList<>();

        for (Repository repository : repositories) {
            if (repository.isLocal()) {
                continue;
            }

            try {
                DependencyDownloadResult result = downloadDependencyAndPom(repository, dependency, localJarPath, localPomPath);

                String cleanRepoUrl = getCleanRepositoryUrl(repository);
                if (result.jarDownloaded()) {
                    logger.info("Downloaded " + dependency + " from " + cleanRepoUrl);
                }

                return result.jarPath();
            } catch (DependencyException e) {
                exceptions.add(e);
                logger.debug("Failed to download " + dependency + " from " + getCleanRepositoryUrl(repository) + ": " + e.getMessage());
            }
        }

        DependencyException exception = new DependencyException("Failed to download dependency " + dependency + " from any repository");
        exceptions.forEach(exception::addSuppressed);

        logger.error("Failed to download dependency from any repository: " + dependency);
        throw exception;
    }

    /**
     * Tries to download only the POM file for an existing dependency.
     *
     * @param dependency the dependency
     * @param localPomPath the local path for the POM file
     */
    private void tryDownloadPomOnly(@NotNull Dependency dependency, @NotNull Path localPomPath) {
        for (Repository repository : repositories) {
            if (repository.isLocal()) {
                continue;
            }

            try {
                downloadPomAndSave(repository, dependency, localPomPath);
                logger.debug("Downloaded POM for cached dependency: " + dependency);
                return;
            } catch (Exception e) { }
        }
    }

    /**
     * Downloads both JAR and POM files for a dependency from a specific repository.
     *
     * @param repository the repository to download from
     * @param dependency the dependency to download
     * @param localJarPath the local path for the JAR file
     * @param localPomPath the local path for the POM file
     * @return the download result
     * @throws DependencyException if download fails
     */
    @NotNull
    private DependencyDownloadResult downloadDependencyAndPom(@NotNull Repository repository,
                                                              @NotNull Dependency dependency,
                                                              @NotNull Path localJarPath,
                                                              @NotNull Path localPomPath) {
        boolean jarDownloaded = false;
        boolean pomDownloaded = false;

        if (!Files.exists(localJarPath) || !isValidJarFile(localJarPath)) {
            downloadJarAndSave(repository, dependency, localJarPath);
            jarDownloaded = true;
        }

        if (!Files.exists(localPomPath) || !isValidPomFile(localPomPath)) {
            try {
                downloadPomAndSave(repository, dependency, localPomPath);
                pomDownloaded = true;
            } catch (DependencyException e) {
                logger.warn("Failed to download POM for " + dependency + " from " + getCleanRepositoryUrl(repository) + ": " + e.getMessage());
            }
        }

        return new DependencyDownloadResult(localJarPath, jarDownloaded, pomDownloaded);
    }

    /**
     * Downloads and saves a JAR file.
     *
     * @param repository the repository to download from
     * @param dependency the dependency
     * @param localFile the local file path
     * @throws DependencyException if download or save fails
     */
    private void downloadJarAndSave(@NotNull Repository repository, @NotNull Dependency dependency, @NotNull Path localFile) {
        try {
            byte[] jarBytes = downloadFile(dependency.toMavenJar(repository).toURL(), "JAR");
            saveFile(jarBytes, localFile);

            if (!isValidJarFile(localFile)) {
                throw new DependencyException("Downloaded JAR is invalid or corrupted: " + dependency);
            }
        } catch (FileNotFoundException | NoSuchFileException e) {
            throw new DependencyException("JAR not found in repository " + repository + ": " + dependency.toMavenJar(repository), e);
        } catch (IOException e) {
            throw new DependencyException("Failed to save JAR for dependency " + dependency, e);
        }
    }

    /**
     * Downloads and saves a POM file.
     *
     * @param repository the repository to download from
     * @param dependency the dependency
     * @param localFile the local file path
     * @throws DependencyException if download or save fails
     */
    private void downloadPomAndSave(@NotNull Repository repository, @NotNull Dependency dependency, @NotNull Path localFile) {
        try {
            byte[] pomBytes = downloadFile(dependency.toPomXml(repository).toURL(), "POM");
            saveFile(pomBytes, localFile);

            if (!isValidPomFile(localFile)) {
                throw new DependencyException("Downloaded POM is invalid or corrupted: " + dependency);
            }
        } catch (FileNotFoundException | NoSuchFileException e) {
            throw new DependencyException("POM not found in repository " + repository + ": " + dependency.toPomXml(repository), e);
        } catch (IOException e) {
            throw new DependencyException("Failed to save POM for dependency " + dependency, e);
        }
    }

    /**
     * Downloads a file from a URL.
     *
     * @param fileUrl the URL to download from
     * @param fileType the type of file for logging
     * @return the downloaded bytes
     * @throws IOException if download fails
     */
    @NotNull
    private byte[] downloadFile(@NotNull URL fileUrl, @NotNull String fileType) throws IOException {
        URLConnection connection = fileUrl.openConnection();

        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", "Quark-LibraryManager/1.0");

        logger.debug("Downloading " + fileType + " from: " + fileUrl);

        try (InputStream inputStream = connection.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();

            if (bytes.length == 0) {
                throw new IOException("Empty " + fileType + " file downloaded from: " + fileUrl);
            }

            return bytes;
        }
    }

    /**
     * Saves bytes to a file.
     *
     * @param bytes the bytes to save
     * @param filePath the target file path
     * @throws IOException if save fails
     */
    private void saveFile(@NotNull byte[] bytes, @NotNull Path filePath) throws IOException {
        Path parentDir = filePath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        Files.write(filePath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Gets a clean, user-friendly repository URL for logging.
     *
     * @param repository the repository
     * @return the clean URL
     */
    @NotNull
    private String getCleanRepositoryUrl(@NotNull Repository repository) {
        String url = repository.getUrl();

        return switch (url) {
            case "https://repo1.maven.org/maven2" -> "https://repo.maven.apache.org/maven2";
            case "https://maven.google.com/maven2" -> "https://maven-central.storage-download.googleapis.com/maven2";
            default -> url;
        };
    }

    /**
     * Validates that a file is a valid JAR file.
     *
     * @param jarFile the JAR file to validate
     * @return true if valid
     */
    private boolean isValidJarFile(@NotNull Path jarFile) {
        try {
            return Files.exists(jarFile) &&
                    Files.isRegularFile(jarFile) &&
                    Files.size(jarFile) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Validates that a file is a valid POM file.
     *
     * @param pomFile the POM file to validate
     * @return true if valid
     */
    private boolean isValidPomFile(@NotNull Path pomFile) {
        try {
            if (!Files.exists(pomFile) || !Files.isRegularFile(pomFile) || Files.size(pomFile) == 0) {
                return false;
            }

            String content = Files.readString(pomFile).trim();

            if (content.isEmpty()) {
                return false;
            }

            boolean hasValidXmlStructure = content.contains("<") && content.contains(">");
            boolean hasProjectTag = content.contains("<project") || content.contains("<project>");
            boolean isNotHtmlError = !content.toLowerCase().contains("<html") &&
                    !content.toLowerCase().contains("<!doctype html");

            return hasValidXmlStructure && hasProjectTag && isNotHtmlError;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Gets a POM file for a dependency if it exists locally.
     *
     * @param dependency the dependency
     * @return the POM file path or null if not found
     * @throws NullPointerException if dependency is null
     */
    @Nullable
    public Path getPomFile(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");

        Path pomPath = dependency.toPomXml(localRepository).toPath();
        return (Files.exists(pomPath) && isValidPomFile(pomPath)) ? pomPath : null;
    }

    /**
     * Checks if a POM file exists for a dependency.
     *
     * @param dependency the dependency to check
     * @return true if POM file exists
     * @throws NullPointerException if dependency is null
     */
    public boolean hasPomFile(@NotNull Dependency dependency) {
        return getPomFile(dependency) != null;
    }

    /**
     * Gets the number of configured repositories.
     *
     * @return the repository count
     */
    public int getRepositoryCount() {
        return repositories.size();
    }

    /**
     * Gets the local repository.
     *
     * @return the local repository
     */
    @NotNull
    public Repository getLocalRepository() {
        return localRepository;
    }

    @Override
    public String toString() {
        return "DependencyDownloader{" +
                "repositories=" + repositories.size() +
                ", localRepository=" + localRepository +
                '}';
    }

    /**
     * Result of downloading a dependency.
     *
     * @param jarPath the path to the JAR file
     * @param jarDownloaded whether the JAR was downloaded
     * @param pomDownloaded whether the POM was downloaded
     */
    private record DependencyDownloadResult(@NotNull Path jarPath, boolean jarDownloaded, boolean pomDownloaded) {
        private DependencyDownloadResult(@NotNull Path jarPath, boolean jarDownloaded, boolean pomDownloaded) {
            this.jarPath = requireNonNull(jarPath, "JAR path cannot be null");
            this.jarDownloaded = jarDownloaded;
            this.pomDownloaded = pomDownloaded;
        }
    }
}
