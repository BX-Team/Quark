package org.bxteam.quark.gradle.devserver

import java.io.File
import java.io.Serializable

/**
 * A plugin installed into a dev server next to the project's own plugin. Only a description:
 * URLs are resolved when the task runs, never during configuration.
 */
sealed interface PluginSource : Serializable {
    /** A version of a Modrinth project; the file for the server's loader is picked. */
    data class Modrinth(val project: String, val version: String) : PluginSource

    /** A version of a Hangar project; the download for the server's platform is picked. */
    data class Hangar(val project: String, val version: String) : PluginSource

    /** An asset of a GitHub release. */
    data class GitHub(val owner: String, val repository: String, val tag: String, val fileName: String) : PluginSource

    /** The first artifact of the last successful Jenkins build whose path matches [artifactPattern]. */
    data class Jenkins(val url: String, val job: String, val artifactPattern: String) : PluginSource

    /** A direct download. */
    data class Url(val url: String) : PluginSource

    /** A local file, copied unless it already exists and [overwrite] is false. */
    data class LocalFile(val file: File, val overwrite: Boolean) : PluginSource
}

/**
 * The `plugins { }` block of a dev server.
 */
class DevServerPlugins internal constructor(private val sources: MutableList<PluginSource>) {
    /** Installs a Modrinth project version, e.g. `modrinth("worldedit", "7.3.12")`. */
    fun modrinth(project: String, version: String) {
        sources.add(PluginSource.Modrinth(project, version))
    }

    /** Installs a Hangar project version, e.g. `hangar("squaremap", "1.3.5")`. */
    fun hangar(project: String, version: String) {
        sources.add(PluginSource.Hangar(project, version))
    }

    /** Installs a GitHub release asset, e.g. `github("NEZNAMY", "TAB", "5.2.0", "TAB.v5.2.0.jar")`. */
    fun github(owner: String, repository: String, tag: String, fileName: String) {
        sources.add(PluginSource.GitHub(owner, repository, tag, fileName.trim()))
    }

    /** Installs an artifact of the last successful Jenkins build. */
    fun jenkins(url: String, job: String, artifactPattern: Regex) {
        sources.add(PluginSource.Jenkins(url, job, artifactPattern.pattern))
    }

    /** Installs an artifact of the last successful Jenkins build (Groovy friendly). */
    fun jenkins(url: String, job: String, artifactPattern: String) {
        sources.add(PluginSource.Jenkins(url, job, artifactPattern))
    }

    /** Downloads a plugin from a URL. */
    fun url(url: String) {
        sources.add(PluginSource.Url(url))
    }

    /** Copies a local plugin file. */
    @JvmOverloads
    fun file(file: File, overwrite: Boolean = false) {
        sources.add(PluginSource.LocalFile(file, overwrite))
    }
}
