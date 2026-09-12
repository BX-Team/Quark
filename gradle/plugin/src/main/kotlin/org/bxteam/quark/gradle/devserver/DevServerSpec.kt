package org.bxteam.quark.gradle.devserver

import org.bxteam.quark.gradle.RamUnit
import org.bxteam.quark.gradle.ServerType
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import javax.inject.Inject

/**
 * A dev server: `devServer { }` configures the one named `default` (task `runServer`), every other entry of
 * `devServers { }` gets a `run<Name>Server` task.
 *
 * ```kotlin
 * devServer {
 *     version = "1.21.8"
 *     ram(4, GB)
 *     acceptEula()
 *     plugins {
 *         modrinth("worldedit", "7.3.12")
 *     }
 * }
 * ```
 */
abstract class DevServerSpec @Inject constructor(private val name: String) : Named {
    override fun getName(): String = name

    /** Server software. Defaults to the type of `quark.platform`. */
    abstract val type: Property<ServerType>

    /** Minecraft version for servers, proxy version for proxies (e.g. `"3.4.0-SNAPSHOT"`). Required. */
    abstract val version: Property<String>

    /**
     * Server build: `"latest"` (default) checks MCJars on every run and downloads new builds, a build number
     * (`"196"`) pins the server. Without network access the cached JAR is used.
     */
    abstract val build: Property<String>

    /** Java version the server runs on. Defaults to what the Minecraft version needs. */
    abstract val javaVersion: Property<Int>

    /** Maximum heap, e.g. `"2G"`. Defaults to 2 GB. */
    abstract val maxMemory: Property<String>

    /** Whether the server GUI is disabled (Minecraft 1.15.2+). Defaults to `true`. */
    abstract val noGui: Property<Boolean>

    /** Whether the Mojang EULA is accepted for you. Defaults to `false`. */
    abstract val acceptEula: Property<Boolean>

    /** Directory the server runs in. Defaults to `run/<type>` for the default server and `run/<name>` for others. */
    abstract val runDirectory: DirectoryProperty

    /** Whether the default run directory gets a sub-directory per version (`run/<version>/<type>`). Defaults to `false`. */
    abstract val perVersionFolder: Property<Boolean>

    /** The plugin JAR installed into the server. Defaults to the Shadow JAR Quark configures. */
    abstract val inputJar: RegularFileProperty

    /** Plugins installed next to the project's plugin. */
    abstract val pluginSources: ListProperty<PluginSource>

    /** Megabyte unit for [ram]. */
    val MB: RamUnit get() = RamUnit.MB

    /** Gigabyte unit for [ram]. */
    val GB: RamUnit get() = RamUnit.GB

    /** Sets the maximum heap, e.g. `ram(4, GB)`. */
    fun ram(amount: Int, unit: RamUnit) {
        require(amount > 0) { "RAM amount must be positive: $amount" }
        maxMemory.set("$amount${unit.flag}")
    }

    /** Pins the server build, e.g. `build(196)`. */
    fun build(number: Int) {
        build.set(number.toString())
    }

    /** Disables the server GUI. */
    fun noGui() {
        noGui.set(true)
    }

    /** Accepts the Mojang EULA for you. */
    fun acceptEula() {
        acceptEula.set(true)
    }

    /** Installs the output of another archive task instead of the Shadow JAR. */
    fun inputTask(task: TaskProvider<out AbstractArchiveTask>) {
        inputJar.set(task.flatMap { it.archiveFile })
    }

    /** Configures the plugins installed next to the project's plugin. */
    fun plugins(configure: Action<DevServerPlugins>) {
        val added = mutableListOf<PluginSource>()
        configure.execute(DevServerPlugins(added))
        pluginSources.addAll(added)
    }

    internal val taskName: String
        get() = taskName(name)

    internal companion object {
        const val DEFAULT = "default"

        /** `default` → `runServer` (kept from run-server-plugin, IDE run configurations use it), `proxy` → `runProxyServer`. */
        fun taskName(name: String): String =
            if (name == DEFAULT) "runServer" else "run${name.replaceFirstChar { it.uppercaseChar() }}Server"
    }
}
