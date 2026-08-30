package org.bxteam.quark.gradle

import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.Serializable

internal const val GOOGLE_MAVEN_CENTRAL_MIRROR = "https://maven-central.storage-download.googleapis.com/maven2/"

/**
 * A package relocation rule, applied to the shaded JAR and to dependencies loaded at runtime.
 */
data class Relocation(val pattern: String, val newPattern: String) : Serializable

/**
 * Repository DSL of the `quark { repositories { } }` block.
 */
interface RepositoryDsl {
    /**
     * Adds a repository used to download dependencies at runtime.
     */
    fun maven(url: String)

    /**
     * Also uses the Maven repositories of the project to download dependencies at runtime.
     */
    fun includeProjectRepositories()
}

/**
 * Configures Quark.
 *
 * ```kotlin
 * quark {
 *     repositories {
 *         includeProjectRepositories()
 *     }
 *     relocate("com.google.gson", "my.plugin.libs.gson")
 * }
 * ```
 */
abstract class QuarkExtension {
    /**
     * Repositories used to download dependencies at runtime. Defaults to the Google Maven Central mirror;
     * a `repositories { }` block replaces the default.
     */
    abstract val repositories: ListProperty<String>

    /**
     * Whether the Maven repositories of the project are also used at runtime. Defaults to `false`.
     */
    abstract val includeProjectRepositories: Property<Boolean>

    /**
     * Relocations applied to the shaded JAR and to runtime dependencies.
     */
    abstract val relocations: ListProperty<Relocation>

    /**
     * Whether Quark itself (`org.bxteam.quark`) is relocated in the shaded JAR. Defaults to `true`.
     *
     * Every plugin must carry its own copy of Quark in its own package, otherwise two plugins with
     * different Quark versions conflict on one server. Only disable this if you relocate Quark yourself.
     */
    abstract val relocateQuark: Property<Boolean>

    /**
     * Package Quark is relocated to. Defaults to `<project group>.libs.quark`.
     */
    abstract val quarkPackage: Property<String>

    /**
     * Configures the repositories used to download dependencies at runtime, replacing the default.
     */
    fun repositories(configure: Action<RepositoryDsl>) {
        val urls = mutableListOf<String>()
        var includeProject = false

        configure.execute(object : RepositoryDsl {
            override fun maven(url: String) {
                urls.add(url)
            }

            override fun includeProjectRepositories() {
                includeProject = true
            }
        })

        repositories.set(urls)
        includeProjectRepositories.set(includeProject)
    }

    /**
     * Relocates [pattern] to [newPattern] in the shaded JAR and in dependencies loaded at runtime.
     */
    fun relocate(pattern: String, newPattern: String) {
        relocations.add(Relocation(pattern, newPattern))
    }
}
