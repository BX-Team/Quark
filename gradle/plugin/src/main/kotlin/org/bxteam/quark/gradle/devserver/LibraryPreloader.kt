package org.bxteam.quark.gradle.devserver

import org.gradle.api.logging.Logger
import java.io.File
import java.io.Serializable
import java.util.zip.ZipFile

/**
 * A runtime dependency declared in the `quark` configuration, resolved by Gradle.
 */
data class DevLibrary(val group: String, val artifact: String, val version: String, val file: File) : Serializable

/**
 * Copies the `quark` runtime dependencies into the plugin's Quark library directory of a dev server
 * (`plugins/<plugin>/libs`, Maven layout), so `LibraryManager#loadFromGradle()` finds them without downloading.
 *
 * The manifest already lists the full, Gradle-resolved dependency tree, so a minimal POM without dependencies
 * is written next to each JAR; an existing POM is kept.
 */
internal object LibraryPreloader {
    fun preload(pluginJar: File, pluginsDirectory: File, libraries: List<DevLibrary>, logger: Logger) {
        if (libraries.isEmpty()) return

        val pluginName = pluginName(pluginJar)
        if (pluginName == null) {
            logger.warn("Could not read the plugin name from ${pluginJar.name}; Quark will download runtime dependencies on start")
            return
        }

        val libs = File(pluginsDirectory, "$pluginName/libs")
        var copied = 0
        for (library in libraries) {
            val directory = File(libs, "${library.group.replace('.', '/')}/${library.artifact}/${library.version}")
            val jar = File(directory, "${library.artifact}-${library.version}.jar")
            if (!jar.isFile || jar.length() != library.file.length()) {
                library.file.copyTo(jar, overwrite = true)
                copied++
            }
            val pom = File(directory, "${library.artifact}-${library.version}.pom")
            if (!pom.exists()) {
                pom.writeText(
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project><modelVersion>4.0.0</modelVersion>" +
                        "<groupId>${library.group}</groupId><artifactId>${library.artifact}</artifactId>" +
                        "<version>${library.version}</version></project>\n"
                )
            }
        }
        logger.lifecycle("Quark runtime dependencies ready in ${libs.relativeToOrSelf(pluginsDirectory.parentFile)} ($copied copied)")
    }

    /** The data folder name: `name` of plugin.yml / paper-plugin.yml / bungee.yml, or `id` of velocity-plugin.json. */
    internal fun pluginName(pluginJar: File): String? = ZipFile(pluginJar).use { zip ->
        for (descriptor in listOf("paper-plugin.yml", "plugin.yml", "bungee.yml")) {
            val entry = zip.getEntry(descriptor) ?: continue
            val text = zip.getInputStream(entry).bufferedReader().readText()
            Regex("""(?m)^name:\s*["']?([^"'#\r\n]+?)["']?\s*(#.*)?$""").find(text)?.let { return it.groupValues[1].trim() }
        }
        zip.getEntry("velocity-plugin.json")?.let { entry ->
            val text = zip.getInputStream(entry).bufferedReader().readText()
            Regex(""""id"\s*:\s*"([^"]+)"""").find(text)?.let { return it.groupValues[1] }
        }
        null
    }
}
