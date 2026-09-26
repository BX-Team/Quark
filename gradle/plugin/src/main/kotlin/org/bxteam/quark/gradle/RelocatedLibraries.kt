package org.bxteam.quark.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.Serializable
import java.util.zip.ZipFile

private const val RELOCATE_PROPERTY = "relocate"

/**
 * Moves the packages of this `quark` library into the plugin's package (`quark.librariesPackage`), in the shaded
 * JAR and when Quark loads it at runtime. The plugin then always uses its own version, even if the server or another
 * library already provides one in the original package.
 *
 * ```kotlin
 * import org.bxteam.quark.gradle.relocate
 *
 * dependencies {
 *     quark("com.google.code.gson:gson:2.11.0") { relocate = true }
 * }
 * ```
 *
 * In the Groovy DSL: `quark('com.google.code.gson:gson:2.11.0') { ext.relocate = true }`.
 *
 * The packages are read from the library JAR when the plugin is built: every package with classes that is not
 * inside another such package becomes one rule, `com.google.gson` -> `<librariesPackage>.com.google.gson`.
 */
var ModuleDependency.relocate: Boolean
    get() {
        val extra = (this as? ExtensionAware)?.extensions?.extraProperties ?: return false
        return extra.has(RELOCATE_PROPERTY) && extra.get(RELOCATE_PROPERTY) == true
    }
    set(value) {
        val aware = this as? ExtensionAware
            ?: throw UnsupportedOperationException("Cannot mark $group:$name for relocation, it was not created by the dependencies DSL")
        aware.extensions.extraProperties.set(RELOCATE_PROPERTY, value)
    }

/**
 * A resolved `quark` library.
 */
internal data class LibraryArtifact(val module: String, val file: File) : Serializable

/**
 * Finds the packages of the `quark` libraries marked with [relocate] and writes their relocation rules,
 * one `pattern=relocatedPattern` per line.
 */
@CacheableTask
abstract class CollectRelocatedLibraries : DefaultTask() {
    /** `group:name` of the libraries to relocate. */
    @get:Input
    abstract val modules: SetProperty<String>

    /** Package the libraries are moved into. */
    @get:Input
    abstract val librariesPackage: Property<String>

    /** The resolved `quark` libraries; their files are tracked by [classpath]. */
    @get:Internal
    internal abstract val artifacts: ListProperty<LibraryArtifact>

    /** The files of [artifacts], for up-to-date checks. */
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    /** The rules. */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun collect() {
        val selected = modules.get()
        val target = librariesPackage.get()
        val rules = artifacts.get()
            .filter { it.module in selected && it.file.name.endsWith(".jar") }
            .flatMap { artifact ->
                val roots = rootPackages(artifact.file)
                if (roots.isEmpty()) {
                    logger.warn("Quark: ${artifact.module} has no packages to relocate")
                }
                roots.map { "$it=$target.$it" }
            }
            .distinct()

        val missing = selected - artifacts.get().map { it.module }.toSet()
        missing.forEach { logger.warn("Quark: $it is marked with relocate = true but is not a resolved quark dependency") }

        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(rules.joinToString("") { "$it\n" })
    }

    internal companion object {
        /**
         * @return the packages with classes in the JAR that are not inside another such package, dotted
         */
        fun rootPackages(jar: File): List<String> {
            val packages = ZipFile(jar).use { zip ->
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.endsWith(".class") && !it.startsWith("META-INF/") && !it.endsWith("module-info.class") && '/' in it }
                    .map { it.substringBeforeLast('/').replace('/', '.') }
                    .toSortedSet()
            }
            return packages.filter { candidate -> packages.none { it != candidate && candidate.startsWith("$it.") } }
        }

        /**
         * Reads the rules written by [collect].
         */
        fun read(file: File): List<Relocation> = if (!file.isFile) emptyList() else file.readLines()
            .filter { '=' in it }
            .map { Relocation(it.substringBefore('='), it.substringAfter('=')) }
    }
}
