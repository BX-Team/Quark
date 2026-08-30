package org.bxteam.quark.gradle

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * The `org.bxteam.quark` plugin.
 *
 * - adds the `quark` configuration for dependencies downloaded at runtime and writes them, with repositories
 *   and relocations, to the `META-INF/quark/manifest` resource;
 * - adds `repo.bxteam.org` and the Quark BOM, but no Quark modules: add the ones you need yourself;
 * - relocates Quark itself to `<group>.libs.quark` in the shaded JAR, plus your `relocate(...)` rules.
 */
class QuarkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("quark", QuarkExtension::class.java).apply {
            repositories.convention(listOf(GOOGLE_MAVEN_CENTRAL_MIRROR))
            includeProjectRepositories.convention(false)
            relocations.convention(emptyList())
            relocateQuark.convention(true)
            quarkPackage.convention(project.provider { defaultQuarkPackage(project) })
        }

        val quark = project.configurations.register(CONFIGURATION_NAME) {
            isCanBeResolved = true
            isCanBeConsumed = false
            description = "Dependencies downloaded and loaded at runtime by Quark"
        }

        addQuarkRepository(project)

        project.plugins.withType<JavaPlugin> {
            project.configurations.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) { extendsFrom(quark.get()) }
            project.dependencies.add(
                JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME,
                project.dependencies.platform("org.bxteam.quark:quark-bom:$QUARK_VERSION")
            )

            val generateManifest = project.tasks.register<GenerateQuarkManifest>("generateQuarkManifest") {
                group = "build"
                description = "Generates the manifest of dependencies Quark downloads and relocates at runtime"

                // only remote repositories are reachable from a server
                val projectRepositories = project.provider {
                    project.repositories.withType(MavenArtifactRepository::class.java)
                        .filter { it.url.scheme == "https" || it.url.scheme == "http" }
                        .map { it.url.toString() }
                }
                repositories.set(extension.repositories.zip(extension.includeProjectRepositories) { configured, include ->
                    configured to include
                }.zip(projectRepositories) { (configured, include), fromProject ->
                    if (include) configured + fromProject else configured
                })
                dependencies.set(quark.flatMap { configuration ->
                    configuration.incoming.artifacts.resolvedArtifacts.map { artifacts ->
                        artifacts.mapNotNull { (it.id.componentIdentifier as? ModuleComponentIdentifier)?.displayName }
                    }
                })
                relocations.set(extension.relocations.map { rules -> rules.map { "${it.pattern}=${it.newPattern}" } })
                outputDirectory.set(project.layout.buildDirectory.dir("generated/quark/resources"))
            }

            project.extensions.getByType(SourceSetContainer::class.java).named("main") {
                resources.srcDir(generateManifest.map { it.outputDirectory })
            }
        }

        project.afterEvaluate {
            if (!plugins.hasPlugin(SHADOW_PLUGIN_ID)) {
                throw GradleException(
                    "The Quark Gradle plugin requires the Shadow plugin to relocate Quark and your dependencies. " +
                        "Apply id(\"$SHADOW_PLUGIN_ID\") in the project that builds the final plugin JAR."
                )
            }
            configureShadowJar(project, extension)
        }
    }

    private fun addQuarkRepository(project: Project) {
        val snapshot = QUARK_VERSION.endsWith("-SNAPSHOT")
        project.repositories.maven {
            name = "BX Team"
            url = project.uri(if (snapshot) "https://repo.bxteam.org/snapshots" else "https://repo.bxteam.org/releases")
            mavenContent {
                includeGroup("org.bxteam.quark")
            }
        }
    }

    private fun configureShadowJar(project: Project, extension: QuarkExtension) {
        val relocations = extension.relocations.get()
        val quarkPackage = if (extension.relocateQuark.get()) extension.quarkPackage.get() else null

        project.tasks.withType<ShadowJar>().configureEach {
            // relocated service descriptors (ServiceLoader providers of the platform adapters) must be renamed and merged;
            // Shadow drops duplicate entries before transformers run unless they are explicitly included
            filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
            mergeServiceFiles()

            if (quarkPackage != null) {
                relocate(QUARK_PACKAGE, quarkPackage)
            }
            relocations.forEach { relocate(it.pattern, it.newPattern) }
        }
    }

    private fun defaultQuarkPackage(project: Project): String {
        val group = project.group.toString()
        if (group.isBlank()) {
            throw GradleException(
                "Cannot relocate Quark: the project has no group. Set `group = \"...\"` or " +
                    "`quark { quarkPackage = \"my.plugin.libs.quark\" }`"
            )
        }
        return "$group.libs.quark"
    }

    private companion object {
        const val CONFIGURATION_NAME = "quark"
        const val SHADOW_PLUGIN_ID = "com.gradleup.shadow"
        const val QUARK_PACKAGE = "org.bxteam.quark"
    }
}

/**
 * The Quark extension of this project.
 */
val Project.quark: QuarkExtension
    get() = extensions.getByType(QuarkExtension::class.java)
