package org.bxteam.quark.gradle

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.withType

import java.io.File

/**
 * Version of Quark to use for the platform dependency.
 * Automatically updated during release process.
 */
private const val QUARK_VERSION: String = "1.2.1"

class QuarkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (!project.plugins.hasPlugin("com.github.johnrengelman.shadow")) {
            error("ShadowJar is required by the Quark Gradle plugin. Please add ShadowJar v9.0.0+")
        }

        project.extensions.create("quark", QuarkExtension::class.java)

        project.repositories.maven {
            url = project.uri("https://repo.bxteam.org/releases")
        }

        val quark = project.configurations.create("quark") {
            isCanBeResolved = true
            isCanBeConsumed = false
            description = "Marks a dependency for downloading at runtime"
        }

        project.afterEvaluate {
            configurations.getByName("compileOnly").extendsFrom(quark)
            project.addQuarkDependencies()

            val extension = project.quark
            if (extension.relocations.isNotEmpty()) {
                project.configureShadowJarRelocations(extension)
            }
        }

        val outputDir = project.layout.buildDirectory.asFile.get().resolve("quark")
        project.tasks.register("generateQuarkFiles") {
            group = "build"
            description = "Generates information about dependencies to install and relocate at runtime"
            doLast {
                outputDir.mkdirs()

                val extension = project.quark
                project.createRepositoriesFile(outputDir, extension)

                if (extension.relocations.isNotEmpty()) {
                    createRelocationsFile(outputDir, extension)
                }

                createDependenciesFile(outputDir, quark)

                val configFile = outputDir.resolve("quark.properties")
                configFile.writeText(extension.toPropertiesFile())
            }
        }

        project.tasks.withType(Jar::class.java).configureEach {
            dependsOn("generateQuarkFiles")

            from(outputDir) {
                include("dependencies.txt")
                include("relocations.txt")
                include("repositories.txt")
                include("quark.properties")
                into("quark")
            }
        }
    }
}

/**
 * Configures ShadowJar tasks with relocation rules
 */
private fun Project.configureShadowJarRelocations(extension: QuarkExtension) {
    project.plugins.withId("com.github.johnrengelman.shadow") {
        project.tasks.withType<ShadowJar>().configureEach {
            extension.relocations.forEach {
                relocate(it.pattern, it.newPattern)
            }
        }
    }
}

/**
 * Generates the relocations.txt file
 */
private fun createRelocationsFile(outputDir: File, extension: QuarkExtension) {
    val relocationsFile = outputDir.resolve("relocations.txt")
    val relocationRules = extension.relocations.map { "${it.pattern}:${it.newPattern}" }
    relocationsFile.writeText(relocationRules.joinToString("\n"))
}

/**
 * Generates the dependencies.txt file
 */
private fun createDependenciesFile(outputDir: File, quarkConfig: Configuration) {
    val dependenciesFile = outputDir.resolve("dependencies.txt")
    val quarkDependencies = quarkConfig.resolvedConfiguration
        .resolvedArtifacts
        .joinToString("\n") { it.moduleVersion.id.toString() }

    dependenciesFile.writeText(quarkDependencies)
}

/**
 * Generates the repositories.txt file
 */
private fun Project.createRepositoriesFile(outputDir: File, extension: QuarkExtension) {
    val repositoriesFile = outputDir.resolve("repositories.txt")
    val repositories = extension.repositories.toMutableSet()
    if (extension.includeProjectRepositories) {
        project.repositories.forEach {
            if (it is MavenArtifactRepository) {
                repositories.add(it.url.toString())
            }
        }
    }
    repositoriesFile.writeText(repositories.joinToString("\n"))
}

/**
 * Extension property for easier access to QuarkExtension
 */
val Project.quark: QuarkExtension
    get() = extensions.getByType(QuarkExtension::class.java)

/**
 * Adds the Quark platform dependency based on the configuration
 */
private fun Project.addQuarkDependencies() {
    val extension = project.quark
    extension.platform?.let { platform ->
        dependencies.add("api", "org.bxteam.quark:${platform}:${QUARK_VERSION}")
    }
}
