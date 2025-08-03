package org.bxteam.quark.dependency;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * Thread-safe collector for managing dependency resolution and version conflicts.
 *
 * <p>This class collects dependencies during the resolution process and handles
 * version conflicts by keeping the newest version of each dependency. It also
 * manages BOM (Bill of Materials) dependencies separately from regular dependencies.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Thread-safe dependency collection</li>
 *   <li>Automatic version conflict resolution</li>
 *   <li>BOM dependency handling</li>
 *   <li>Duplicate detection and prevention</li>
 * </ul>
 */
public class DependencyCollector {
    private final ConcurrentHashMap<String, Dependency> scannedDependencies = new ConcurrentHashMap<>();

    /**
     * Creates a new empty dependency collector.
     */
    public DependencyCollector() {
    }

    /**
     * Checks if a dependency has already been scanned and is up-to-date.
     *
     * <p>This method considers a dependency as already scanned if:</p>
     * <ul>
     *   <li>A dependency with the same group:artifact exists</li>
     *   <li>The existing version is the same or newer</li>
     *   <li>BOM status is compatible (BOM can be upgraded to non-BOM)</li>
     * </ul>
     *
     * @param dependency the dependency to check
     * @return true if the dependency is already scanned with same or newer version
     * @throws NullPointerException if dependency is null
     */
    public boolean hasScannedDependency(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");

        Dependency existing = scannedDependencies.get(dependency.getGroupArtifactId());
        if (existing == null) {
            return false;
        }

        if (existing.isBom() && !dependency.isBom()) {
            return false;
        }

        return existing.getVersion().equals(dependency.getVersion()) || existing.isNewerThan(dependency);
    }

    /**
     * Adds a dependency to the collection, handling version conflicts.
     *
     * <p>If a dependency with the same group:artifact already exists, this method
     * will keep the newer version. BOM dependencies can be upgraded to regular
     * dependencies if needed.</p>
     *
     * @param dependency the dependency to add
     * @return the dependency that was actually stored (may be different due to conflict resolution)
     * @throws NullPointerException if dependency is null
     */
    @NotNull
    public Dependency addScannedDependency(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");

        String key = dependency.getGroupArtifactId();

        return scannedDependencies.compute(key, (k, existing) -> {
            if (existing == null) {
                return dependency;
            }

            Dependency currentToCompare = existing;
            Dependency newToCompare = dependency;

            if (!existing.isBom() || !dependency.isBom()) {
                currentToCompare = existing.asNotBom();
                newToCompare = dependency.asNotBom();
            }

            return newToCompare.isNewerThan(currentToCompare) ? newToCompare : currentToCompare;
        });
    }

    /**
     * Adds multiple dependencies to the collection.
     *
     * @param dependencies the dependencies to add
     * @throws NullPointerException if dependencies is null
     */
    public void addScannedDependencies(@NotNull Collection<Dependency> dependencies) {
        requireNonNull(dependencies, "Dependencies collection cannot be null");

        dependencies.forEach(this::addScannedDependency);
    }

    /**
     * Gets all scanned dependencies excluding BOM dependencies.
     *
     * <p>This method returns only regular (non-BOM) dependencies that should
     * be downloaded and loaded into the classpath.</p>
     *
     * @return immutable collection of non-BOM dependencies
     */
    @NotNull
    public Collection<Dependency> getScannedDependencies() {
        return scannedDependencies.values().stream()
                .filter(dependency -> !dependency.isBom())
                .toList();
    }

    /**
     * Gets all scanned dependencies including BOM dependencies.
     *
     * @return immutable collection of all dependencies
     */
    @NotNull
    public Collection<Dependency> getAllScannedDependencies() {
        return Collections.unmodifiableCollection(scannedDependencies.values());
    }

    /**
     * Gets BOM dependencies only.
     *
     * @return immutable collection of BOM dependencies
     */
    @NotNull
    public Collection<Dependency> getBomDependencies() {
        return scannedDependencies.values().stream()
                .filter(Dependency::isBom)
                .toList();
    }

    /**
     * Gets the number of scanned dependencies (including BOMs).
     *
     * @return the total number of dependencies
     */
    public int size() {
        return scannedDependencies.size();
    }

    /**
     * Gets the number of non-BOM dependencies.
     *
     * @return the number of regular dependencies
     */
    public int getNonBomCount() {
        return (int) scannedDependencies.values().stream()
                .filter(dep -> !dep.isBom())
                .count();
    }

    /**
     * Gets the number of BOM dependencies.
     *
     * @return the number of BOM dependencies
     */
    public int getBomCount() {
        return (int) scannedDependencies.values().stream()
                .filter(Dependency::isBom)
                .count();
    }

    /**
     * Checks if the collector is empty.
     *
     * @return true if no dependencies have been collected
     */
    public boolean isEmpty() {
        return scannedDependencies.isEmpty();
    }

    /**
     * Clears all collected dependencies.
     */
    public void clear() {
        scannedDependencies.clear();
    }

    /**
     * Checks if a specific dependency key exists.
     *
     * @param groupArtifactId the group:artifact key to check
     * @return true if a dependency with this key exists
     */
    public boolean containsKey(@NotNull String groupArtifactId) {
        requireNonNull(groupArtifactId, "Group artifact ID cannot be null");
        return scannedDependencies.containsKey(groupArtifactId);
    }

    /**
     * Gets a dependency by its group:artifact ID.
     *
     * @param groupArtifactId the group:artifact key
     * @return the dependency or null if not found
     */
    @NotNull
    public Dependency getDependency(@NotNull String groupArtifactId) {
        requireNonNull(groupArtifactId, "Group artifact ID cannot be null");
        return scannedDependencies.get(groupArtifactId);
    }

    @Override
    public String toString() {
        return "DependencyCollector{" +
                "total=" + size() +
                ", regular=" + getNonBomCount() +
                ", bom=" + getBomCount() +
                '}';
    }
}
