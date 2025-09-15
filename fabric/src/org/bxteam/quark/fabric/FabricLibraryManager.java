package org.bxteam.quark.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.bxteam.quark.LibraryManager;
import org.bxteam.quark.classloader.IsolatedClassLoader;
import org.bxteam.quark.dependency.Dependency;
import org.bxteam.quark.fabric.logger.adapters.FabricLogAdapter;
import org.bxteam.quark.logger.LogAdapter;
import org.bxteam.quark.relocation.Relocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Fabric-specific implementation of LibraryManager for Fabric mods.
 *
 * <p>This implementation uses Fabric's launcher to add JARs to the mod's
 * classpath at runtime.</p>
 */
public class FabricLibraryManager extends LibraryManager {
    private final ModContainer modContainer;

    /**
     * Creates a new Fabric library manager.
     *
     * @param modId the id of the mod
     * @param logger the mod logger
     */
    public FabricLibraryManager(@NotNull String modId, @NotNull Logger logger) {
        this(modId, logger, "libs");
    }

    /**
     * Creates a new Fabric library manager.
     *
     * @param modId the id of the mod
     * @param logger the mod logger
     * @param directoryName download directory name
     */
    public FabricLibraryManager(@NotNull String modId, @NotNull Logger logger, @NotNull String directoryName) {
        this(modId, new FabricLogAdapter(logger), directoryName);
    }

    /**
     * Creates a new Fabric library manager.
     *
     * @param modId the id of the mod
     * @param logAdapter the log adapter to use instead of the mod logger
     * @param directoryName download directory name
     */
    public FabricLibraryManager(@NotNull String modId, @NotNull LogAdapter logAdapter, @NotNull String directoryName) {
        super(logAdapter, FabricLoader.getInstance().getConfigDir().resolve(modId), directoryName);
        modContainer = FabricLoader.getInstance().getModContainer(requireNonNull(modId, modId)).orElseThrow(() -> new NullPointerException("modContainer"));
    }

    @Override
    protected void addToClasspath(@NotNull Path jarPath) {
        FabricLauncherBase.getLauncher().addToClassPath(jarPath);
    }

    @Override
    protected @Nullable InputStream getResourceAsStream(@NotNull String resourcePath) {
        try {
            return Files.newInputStream(requireNonNull(modContainer.findPath(resourcePath).orElse(null)));
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Loads a dependency by Maven coordinates.
     *
     * @param groupId the Maven group ID
     * @param artifactId the Maven artifact ID
     * @param version the dependency version
     * @throws NullPointerException if any parameter is null
     */
    public void loadDependency(@NotNull String groupId, @NotNull String artifactId, @NotNull String version) {
        requireNonNull(groupId, "Group ID cannot be null");
        requireNonNull(artifactId, "Artifact ID cannot be null");
        requireNonNull(version, "Version cannot be null");

        loadDependency(Dependency.of(groupId, artifactId, version));
    }

    /**
     * Loads a single dependency.
     *
     * @param dependency the dependency to load
     * @throws NullPointerException if dependency is null
     */
    public void loadDependency(@NotNull Dependency dependency) {
        requireNonNull(dependency, "Dependency cannot be null");
        loadDependencies(Collections.singletonList(dependency));
    }

    /**
     * Loads dependencies without relocations.
     *
     * @param dependencies the list of dependencies to load
     * @throws NullPointerException if dependencies is null
     */
    public void loadDependencies(@NotNull List<Dependency> dependencies) {
        loadDependencies(dependencies, Collections.emptyList());
    }

    /**
     * Loads dependencies with relocations into the main plugin classpath.
     *
     * @param dependencies the list of dependencies to load
     * @param relocations the list of relocations to apply
     * @throws NullPointerException if any parameter is null
     */
    public void loadDependencies(@NotNull List<Dependency> dependencies, @NotNull List<Relocation> relocations) {
        requireNonNull(dependencies, "Dependencies cannot be null");
        requireNonNull(relocations, "Relocations cannot be null");

        super.loadDependencies(dependencies, relocations);
    }

    /**
     * Loads dependencies into an isolated class loader.
     *
     * @param isolatedClassLoader the isolated class loader
     * @param dependencies the list of dependencies to load
     * @param relocations the list of relocations to apply
     * @throws NullPointerException if any parameter is null
     */
    public void loadDependenciesIsolated(@NotNull IsolatedClassLoader isolatedClassLoader,
                                         @NotNull List<Dependency> dependencies,
                                         @NotNull List<Relocation> relocations) {
        super.loadDependencies(isolatedClassLoader, dependencies, relocations);
    }

    /**
     * Loads dependencies from Gradle plugin generated metadata.
     *
     * <p>This method reads the metadata files generated by the Quark Gradle plugin
     * and loads all configured dependencies with their associated repositories and
     * relocations into the main plugin classpath.</p>
     *
     * @throws org.bxteam.quark.gradle.GradleMetadataLoader.GradleMetadataException if metadata loading fails
     * @throws LibraryLoadException if dependency loading fails
     */
    public void loadFromGradle() {
        super.loadFromGradle();
    }

    /**
     * Loads dependencies from Gradle metadata into an isolated class loader.
     *
     * @param isolatedClassLoader the isolated class loader to load dependencies into
     * @throws NullPointerException if classLoader is null
     * @throws org.bxteam.quark.gradle.GradleMetadataLoader.GradleMetadataException if metadata loading fails
     * @throws LibraryLoadException if dependency loading fails
     */
    public void loadFromGradleIsolated(@NotNull IsolatedClassLoader isolatedClassLoader) {
        super.loadFromGradle(isolatedClassLoader);
    }

    /**
     * Loads dependencies from Gradle metadata into a named isolated class loader.
     *
     * @param loaderId the unique identifier for the class loader
     * @throws NullPointerException if loaderId is null
     * @throws org.bxteam.quark.gradle.GradleMetadataLoader.GradleMetadataException if metadata loading fails
     * @throws LibraryLoadException if dependency loading fails
     */
    public void loadFromGradleIsolated(@NotNull String loaderId) {
        super.loadFromGradleIsolated(loaderId);
    }
}
