package org.bxteam.quark.gradle

import org.bxteam.quark.gradle.devserver.DevServerSpec
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import java.io.Serializable
import javax.inject.Inject

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
 *     platform = ServerPlatform.PAPER
 *     modules(QuarkModule.DEPENDENCY, QuarkModule.UPDATE)
 *
 *     repositories {
 *         includeProjectRepositories()
 *     }
 *     relocate("com.google.gson", "my.plugin.libs.gson")
 *
 *     devServer {
 *         version = "1.21.8"
 *         acceptEula()
 *     }
 * }
 * ```
 */
abstract class QuarkExtension @Inject constructor(objects: ObjectFactory) {
    /**
     * The platform the plugin targets. Adds its Quark adapter (e.g. `quark-paper`) to `implementation` and selects
     * the default server type of dev servers. Optional: without it no adapter is added.
     */
    abstract val platform: Property<ServerPlatform>

    /**
     * Optional Quark modules added to `implementation`, in the version of this plugin.
     */
    abstract val modules: SetProperty<QuarkModule>

    /**
     * Adds Quark modules, e.g. `modules(QuarkModule.DEPENDENCY, QuarkModule.CONFIG)`.
     */
    fun modules(vararg modules: QuarkModule) {
        this.modules.addAll(*modules)
    }

    /**
     * Adds Quark modules by name, e.g. `modules 'dependency', 'update'` in the Groovy DSL.
     */
    fun modules(vararg names: String) {
        modules.addAll(names.map { QuarkModule.of(it) })
    }

    /**
     * Dev servers. Each entry registers a `run<Name>Server` task, the entry `default` registers `runServer`.
     * Without entries no task is registered.
     */
    val devServers: NamedDomainObjectContainer<DevServerSpec> = objects.domainObjectContainer(DevServerSpec::class.java)

    /**
     * Sets [platform] by name, e.g. `platform 'paper'` in the Groovy DSL.
     */
    fun platform(name: String) {
        platform.set(ServerPlatform.of(name))
    }

    /**
     * Sets [platform].
     */
    fun platform(value: ServerPlatform) {
        platform.set(value)
    }

    /**
     * Configures the dev server named `default`, run with the `runServer` task.
     */
    fun devServer(configure: Action<DevServerSpec>) {
        if (DevServerSpec.DEFAULT in devServers.names) {
            devServers.named(DevServerSpec.DEFAULT, configure)
        } else {
            devServers.register(DevServerSpec.DEFAULT, configure)
        }
    }

    /**
     * Configures the dev servers.
     */
    fun devServers(configure: Action<NamedDomainObjectContainer<DevServerSpec>>) {
        configure.execute(devServers)
    }

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
     * Package that `quark` libraries marked with `relocate = true` are moved into, each under its original package
     * name (`com.google.gson` -> `<librariesPackage>.com.google.gson`). Defaults to `<project group>.libs`.
     */
    abstract val librariesPackage: Property<String>

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
