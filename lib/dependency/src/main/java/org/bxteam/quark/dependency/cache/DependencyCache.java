package org.bxteam.quark.dependency.cache;

import org.bxteam.quark.pom.model.MavenMetadata;
import org.bxteam.quark.pom.model.PomInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages caching of dependency resolution data including POMs, metadata, and versions.
 */
public class DependencyCache {
    private final Map<String, PomInfo> pomCache = new ConcurrentHashMap<>();
    private final Map<String, MavenMetadata> metadataCache = new ConcurrentHashMap<>();
    private final Map<String, String> resolvedVersionCache = new ConcurrentHashMap<>();
    private final Set<String> processedDependencies = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> dependencyDepths = new ConcurrentHashMap<>();
    private final Map<String, String> globalDependencyManagement = new ConcurrentHashMap<>();

    /**
     * Clears all caches.
     */
    public void clearAll() {
        pomCache.clear();
        metadataCache.clear();
        resolvedVersionCache.clear();
        processedDependencies.clear();
        dependencyDepths.clear();
        globalDependencyManagement.clear();
    }

    /**
     * Gets a cached POM by its coordinates.
     *
     * @param coordinates the dependency coordinates
     * @return the cached POM info, or null if not found
     */
    @Nullable
    public PomInfo getPom(@NotNull String coordinates) {
        return pomCache.get(coordinates);
    }

    /**
     * Caches a POM.
     *
     * @param coordinates the dependency coordinates
     * @param pomInfo the POM info to cache
     */
    public void cachePom(@NotNull String coordinates, @NotNull PomInfo pomInfo) {
        pomCache.put(coordinates, pomInfo);
    }

    /**
     * Gets cached metadata for a dependency.
     *
     * @param key the dependency key (groupId:artifactId)
     * @return the cached metadata, or null if not found
     */
    @Nullable
    public MavenMetadata getMetadata(@NotNull String key) {
        return metadataCache.get(key);
    }

    /**
     * Caches metadata for a dependency.
     *
     * @param key the dependency key (groupId:artifactId)
     * @param metadata the metadata to cache
     */
    public void cacheMetadata(@NotNull String key, @NotNull MavenMetadata metadata) {
        metadataCache.put(key, metadata);
    }

    /**
     * Gets a cached resolved version.
     *
     * @param key the version key (groupId:artifactId)
     * @return the cached version, or null if not found
     */
    @Nullable
    public String getResolvedVersion(@NotNull String key) {
        return resolvedVersionCache.get(key);
    }

    /**
     * Caches a resolved version.
     *
     * @param key the version key (groupId:artifactId)
     * @param version the resolved version
     */
    public void cacheResolvedVersion(@NotNull String key, @NotNull String version) {
        resolvedVersionCache.put(key, version);
    }

    /**
     * Checks if a dependency has been processed.
     *
     * @param coordinates the dependency coordinates
     * @return true if the dependency has been processed
     */
    public boolean isProcessed(@NotNull String coordinates) {
        return processedDependencies.contains(coordinates);
    }

    /**
     * Marks a dependency as processed.
     *
     * @param coordinates the dependency coordinates
     */
    public void markAsProcessed(@NotNull String coordinates) {
        processedDependencies.add(coordinates);
    }

    /**
     * Gets the depth of a dependency.
     *
     * @param coordinates the dependency coordinates
     * @param defaultValue the default value if not found
     * @return the dependency depth
     */
    public int getDependencyDepth(@NotNull String coordinates, int defaultValue) {
        return dependencyDepths.getOrDefault(coordinates, defaultValue);
    }

    /**
     * Sets the depth of a dependency.
     *
     * @param coordinates the dependency coordinates
     * @param depth the depth to set
     */
    public void setDependencyDepth(@NotNull String coordinates, int depth) {
        dependencyDepths.put(coordinates, depth);
    }

    /**
     * Gets the global dependency management map.
     *
     * @return the global dependency management map
     */
    @NotNull
    public Map<String, String> getGlobalDependencyManagement() {
        return globalDependencyManagement;
    }

    /**
     * Adds entries to the global dependency management.
     *
     * @param entries the entries to add
     */
    public void addGlobalDependencyManagement(@NotNull Map<String, String> entries) {
        globalDependencyManagement.putAll(entries);
    }
}

