package org.bxteam.quark.gradle

import org.gradle.api.Action

private const val GOOGLE_MAVEN_CENTRAL_MIRROR = "https://maven-central.storage-download.googleapis.com/maven2/"

/**
 * Data class representing a relocation rule
 */
data class Relocation(val pattern: String, val newPattern: String)

/**
 * Interface for repository DSL configuration
 */
interface RepositoryDsl {
    /**
     * Adds the given repository to the repositories list
     */
    fun maven(url: String)

    /**
     * Tells Quark to include the project repositories for
     * resolving dependencies
     */
    fun includeProjectRepositories()
}

/**
 * A basic implementation of [RepositoryDsl]
 */
internal class BasicRepositoryDsl : RepositoryDsl {
    /**
     * The repositories list
     */
    val repositories = mutableListOf<String>()

    /**
     * Should project repositories be included?
     */
    var includeProjectRepositories = false

    /**
     * Adds the given repository to the repositories list
     */
    override fun maven(url: String) {
        repositories.add(url)
    }

    /**
     * Tells Quark to include the project repositories for
     * resolving dependencies
     */
    override fun includeProjectRepositories() {
        includeProjectRepositories = true
    }
}

/**
 * Configure Quark properties
 */
open class QuarkExtension {
    /**
     * The platform type to use (e.g., "paper", "bukkit", "velocity", etc.)
     */
    var platform: String? = null

    /**
     * The repositories URLs
     */
    private var _repositories = mutableListOf(GOOGLE_MAVEN_CENTRAL_MIRROR)

    /**
     * The relocation rules
     */
    private var _relocations = mutableListOf<Relocation>()

    /**
     * The currently added repositories
     */
    val repositories: List<String> get() = _repositories

    /**
     * The current relocation rules
     */
    val relocations: List<Relocation> get() = _relocations

    /**
     * Should project repositories be remembered for downloading
     * repositories at runtime?
     */
    internal var includeProjectRepositories = false

    /**
     * Configures the repositories that are used for downloading dependencies.
     *
     * See [RepositoryDsl]
     */
    fun repositories(configure: Action<RepositoryDsl>) {
        val dsl = BasicRepositoryDsl()
        configure.execute(dsl)
        _repositories = dsl.repositories
        includeProjectRepositories = dsl.includeProjectRepositories
    }

    /**
     * Adds a relocation rule
     */
    fun relocate(pattern: String, newPattern: String) {
        _relocations.add(Relocation(pattern, newPattern))
    }
}
