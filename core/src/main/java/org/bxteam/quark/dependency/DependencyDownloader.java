package org.bxteam.quark.dependency;

import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.repository.Repository;
import org.jetbrains.annotations.NotNull;

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
 * Downloads dependency JARs from Maven repositories.
 */
public class DependencyDownloader {
    private final Logger logger;
    private final Repository localRepository;
    private final List<Repository> repositories;

    public DependencyDownloader(@NotNull Logger logger, @NotNull Repository localRepository, @NotNull List<Repository> repositories) {
        this.logger = requireNonNull(logger, "Logger cannot be null");
        this.localRepository = requireNonNull(localRepository, "Local repository cannot be null");
        this.repositories = new ArrayList<>(requireNonNull(repositories, "Repositories cannot be null"));
    }

    @NotNull
    public Path downloadDependency(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");

        try {
            return tryDownloadDependency(dependency);
        } catch (URISyntaxException e) {
            throw new DependencyException("Invalid dependency coordinates: " + dependency, e);
        }
    }

    @NotNull
    private Path tryDownloadDependency(@NotNull Dependency dependency) throws URISyntaxException {
        Path localPath = dependency.toMavenJar(localRepository).toPath();

        if (Files.exists(localPath) && isValidJarFile(localPath)) {
            logger.debug("Using cached dependency: " + dependency);
            return localPath;
        }

        List<DependencyException> exceptions = new ArrayList<>();

        for (Repository repository : repositories) {
            if (repository.isLocal()) {
                continue;
            }

            try {
                Path downloadedPath = downloadJarAndSave(repository, dependency, localPath);

                String cleanRepoUrl = getCleanRepositoryUrl(repository);
                logger.info("Downloaded " + dependency + " from " + cleanRepoUrl);

                return downloadedPath;

            } catch (DependencyException e) {
                exceptions.add(e);
                String cleanRepoUrl = getCleanRepositoryUrl(repository);
                logger.debug("Failed to download " + dependency + " from " + cleanRepoUrl + ": " + e.getMessage());
            }
        }

        DependencyException exception = new DependencyException("Failed to download dependency " + dependency + " from any repository");
        exceptions.forEach(exception::addSuppressed);

        logger.error("Failed to download dependency from any repository: " + dependency);
        throw exception;
    }

    /**
     * Gets a clean, user-friendly repository URL for logging.
     */
    @NotNull
    private String getCleanRepositoryUrl(@NotNull Repository repository) {
        String url = repository.getUrl();

        if (url.equals("https://repo1.maven.org/maven2")) {
            return "https://repo.maven.apache.org/maven2";
        }

        return url;
    }

    @NotNull
    private Path downloadJarAndSave(@NotNull Repository repository, @NotNull Dependency dependency, @NotNull Path localFile) {
        try {
            byte[] jarBytes = downloadJar(repository, dependency);

            Path parentDir = localFile.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            Files.write(localFile, jarBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            if (!isValidJarFile(localFile)) {
                throw new DependencyException("Downloaded JAR is invalid or corrupted: " + dependency);
            }

            return localFile;
        } catch (FileNotFoundException | NoSuchFileException e) {
            throw new DependencyException("Dependency not found in repository " + repository + ": " + dependency.toMavenJar(repository), e);
        } catch (IOException e) {
            throw new DependencyException("Failed to save dependency " + dependency, e);
        }
    }

    @NotNull
    private byte[] downloadJar(@NotNull Repository repository, @NotNull Dependency dependency) throws IOException {
        URL jarUrl = dependency.toMavenJar(repository).toURL();
        URLConnection connection = jarUrl.openConnection();

        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);

        connection.setRequestProperty("User-Agent", "Quark-LibraryManager/1.0");

        try (InputStream inputStream = connection.getInputStream()) {
            byte[] bytes = inputStream.readAllBytes();

            if (bytes.length == 0) {
                throw new IOException("Empty JAR file downloaded for: " + dependency);
            }

            return bytes;
        }
    }

    private boolean isValidJarFile(@NotNull Path jarFile) {
        try {
            return Files.exists(jarFile) &&
                    Files.isRegularFile(jarFile) &&
                    Files.size(jarFile) > 0;
        } catch (IOException e) {
            return false;
        }
    }

    public int getRepositoryCount() {
        return repositories.size();
    }

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
}
