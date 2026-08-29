package org.bxteam.quark.dependency.resolver;

import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.dependency.cache.DependencyCache;
import org.bxteam.quark.dependency.downloader.DependencyDownloader;
import org.bxteam.quark.logger.Logger;
import org.bxteam.quark.pom.model.MavenMetadata;
import org.bxteam.quark.pom.model.PomInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Handles version resolution for dependencies using metadata and dependency management.
 */
public class VersionResolver {
    private final Logger logger;
    private final DependencyDownloader downloader;
    private final DependencyCache cache;

    /**
     * Creates a new VersionResolver.
     *
     * @param logger the logger for reporting version resolution
     * @param downloader the dependency downloader
     * @param cache the dependency cache
     */
    public VersionResolver(@NotNull Logger logger,
                          @NotNull DependencyDownloader downloader,
                          @NotNull DependencyCache cache) {
        this.logger = requireNonNull(logger, "Logger cannot be null");
        this.downloader = requireNonNull(downloader, "Downloader cannot be null");
        this.cache = requireNonNull(cache, "Cache cannot be null");
    }

    /**
     * Resolves dependency version using metadata if version is missing.
     *
     * @param dependency the dependency to resolve
     * @return the dependency with resolved version
     * @throws RuntimeException if version cannot be resolved
     */
    @NotNull
    public Dependency resolveDependencyVersion(@NotNull Dependency dependency) {
        if (dependency.getVersion() != null && !dependency.getVersion().trim().isEmpty()) {
            return dependency;
        }

        String versionKey = dependency.getGroupId() + ":" + dependency.getArtifactId();
        String cachedVersion = cache.getResolvedVersion(versionKey);

        if (cachedVersion != null) {
            return dependency.withVersion(cachedVersion);
        }

        try {
            MavenMetadata metadata = downloader.downloadAndParseMetadata(dependency);
            if (metadata != null) {
                String bestVersion = metadata.getBestVersion();
                if (bestVersion != null) {
                    cache.cacheResolvedVersion(versionKey, bestVersion);
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
     * Resolves dependency version using POM dependency management and global management.
     *
     * @param dependency the dependency to resolve
     * @param pomInfo the POM information
     * @return the dependency with resolved version
     */
    @NotNull
    public Dependency resolveDependencyFromManagement(@NotNull Dependency dependency, @NotNull PomInfo pomInfo) {
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

        if ((version == null || version.trim().isEmpty())) {
            Map<String, String> globalManagement = cache.getGlobalDependencyManagement();
            if (!globalManagement.isEmpty()) {
                version = globalManagement.get(dependencyKey);
                if (version != null && !version.trim().isEmpty()) {
                    logger.debug("Found version in global dependency management for " + dependencyKey + ": " + version);
                }
            }
        }

        if (version == null || version.trim().isEmpty()) {
            version = cache.getResolvedVersion(dependencyKey);
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
}

