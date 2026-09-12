package org.bxteam.quark.gradle.devserver

import org.bxteam.quark.gradle.ServerType
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File

/**
 * Installs [PluginSource]s into a dev server's plugins directory. A file that already exists is kept,
 * so restarting the server does not download everything again.
 */
internal class PluginInstaller(private val pluginsDirectory: File, private val type: ServerType, private val logger: Logger) {
    fun install(sources: List<PluginSource>) {
        val failures = mutableListOf<String>()
        for (source in sources) {
            try {
                install(source)
            } catch (e: Exception) {
                failures += "$source: ${e.message}"
            }
        }
        if (failures.isNotEmpty()) {
            throw GradleException("Failed to install ${failures.size} plugin(s):\n- " + failures.joinToString("\n- "))
        }
    }

    private fun install(source: PluginSource) {
        when (source) {
            is PluginSource.Modrinth -> modrinth(source)
            is PluginSource.Hangar -> hangar(source)
            is PluginSource.GitHub -> download(
                "https://github.com/${source.owner}/${source.repository}/releases/download/${source.tag}/${Downloads.encode(source.fileName)}",
                source.fileName
            )
            is PluginSource.Jenkins -> jenkins(source)
            is PluginSource.Url -> download(source.url, source.url.substringBefore('?').substringAfterLast('/'))
            is PluginSource.LocalFile -> copy(source)
        }
    }

    private fun copy(source: PluginSource.LocalFile) {
        if (!source.file.isFile) throw GradleException("File not found: ${source.file}")
        val target = File(pluginsDirectory, source.file.name)
        if (source.overwrite || !target.exists()) {
            source.file.copyTo(target, overwrite = true)
            logger.lifecycle("Copied ${source.file.name}")
        }
    }

    private fun modrinth(source: PluginSource.Modrinth) {
        val version = Downloads.json("https://api.modrinth.com/v2/project/${Downloads.encode(source.project)}/version/${Downloads.encode(source.version)}").obj()
        val loaders = version["loaders"].arr().map { it.toString() }
        if (loaders.none { type.accepts(it) }) {
            throw GradleException("${source.project} ${source.version} supports $loaders, not ${type.name}")
        }
        val files = version["files"].arr().map { it.obj() }
        val file = files.firstOrNull { it["primary"] == true } ?: files.firstOrNull() ?: throw GradleException("no files")
        download(file["url"].toString(), file["filename"].toString())
    }

    private fun hangar(source: PluginSource.Hangar) {
        val version = Downloads.json("https://hangar.papermc.io/api/v1/projects/${Downloads.encode(source.project)}/versions/${Downloads.encode(source.version)}").obj()
        val downloads = version["downloads"].obj()
        val platform = when {
            type.proxy && type.loader == "velocity" -> "VELOCITY"
            type.proxy -> "WATERFALL"
            else -> "PAPER"
        }
        val download = downloads[platform]?.obj() ?: throw GradleException("no $platform download, available: ${downloads.keys}")
        val url = download["downloadUrl"] as? String ?: download["externalUrl"] as? String ?: throw GradleException("no download URL")
        val name = (download["fileInfo"] as? Map<*, *>)?.get("name")?.toString() ?: "${source.project}-${source.version}.jar"
        download(url, name)
    }

    private fun jenkins(source: PluginSource.Jenkins) {
        val base = source.url.trimEnd('/') + "/job/" + source.job + "/lastSuccessfulBuild"
        val artifacts = Downloads.json("$base/api/json?tree=artifacts%5BrelativePath%5D").obj()["artifacts"].arr()
            .map { it.obj()["relativePath"].toString() }
        val pattern = Regex(source.artifactPattern)
        val artifact = artifacts.firstOrNull { pattern.containsMatchIn(it) }
            ?: throw GradleException("no artifact matches '${source.artifactPattern}', available: $artifacts")
        download("$base/artifact/$artifact", artifact.substringAfterLast('/'))
    }

    private fun download(url: String, fileName: String) {
        if (fileName.isBlank()) throw GradleException("Cannot derive a file name from $url")
        val target = File(pluginsDirectory, fileName)
        if (target.isFile && target.length() > 0) {
            return
        }
        logger.lifecycle("Downloading $fileName...")
        Downloads.file(url, target)
    }
}
