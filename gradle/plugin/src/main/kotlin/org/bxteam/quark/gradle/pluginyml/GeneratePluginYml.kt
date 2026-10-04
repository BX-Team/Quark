package org.bxteam.quark.gradle.pluginyml

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Writes `plugin.yml`, `paper-plugin.yml`, `bungee.yml` or `velocity-plugin.json` from `quark { pluginYml { } }`.
 * Without that block it writes nothing.
 */
@CacheableTask
internal abstract class GeneratePluginYml : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val descriptor: Property<PluginDescriptor>

    /** Descriptor paths in the project's own resource directories; one that exists would clash with the generated file. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val handWritten: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val directory = outputDirectory.get().asFile
        directory.deleteRecursively()
        directory.mkdirs()

        val descriptor = descriptor.orNull ?: return
        val format = descriptor.format()
        handWritten.files.firstOrNull { it.isFile && it.name == format.fileName }?.let {
            throw GradleException("quark { pluginYml { } } generates ${format.fileName}, but $it exists too. Delete that file or remove pluginYml { }.")
        }
        directory.resolve(format.fileName).writeText(descriptor.render(format), Charsets.UTF_8)
    }

    internal companion object {
        val FILE_NAMES = DescriptorFormat.values().map { it.fileName }
    }
}
