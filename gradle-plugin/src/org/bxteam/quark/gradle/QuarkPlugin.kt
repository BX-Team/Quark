package org.bxteam.quark.gradle

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.withType
import java.io.File

class QuarkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (!project.plugins.hasPlugin("com.github.johnrengelman.shadow")) {
            error("ShadowJar is required by the Quark Gradle plugin. Please add ShadowJar v8.1.1+")
        }

        project.extensions.create("quark", QuarkExtension::class.java)

        val quark = project.configurations.create("quark") {
            isCanBeResolved = true
            isCanBeConsumed = false
            description = "Marks a dependency for downloading at runtime"
        }

        project.afterEvaluate {
            configurations.getByName("compileOnly").extendsFrom(quark)
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
                    project.createRelocationsFile(outputDir, extension)
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
 * Generates the relocations.txt file
 */
private fun Project.createRelocationsFile(outputDir: File, extension: QuarkExtension) {
    val relocationsFile = outputDir.resolve("relocations.txt")
    project.plugins.withId("com.github.johnrengelman.shadow") {
        val relocationRules = mutableListOf<String>()
        project.tasks.withType<ShadowJar>().configureEach {
            extension.relocations.forEach {
                relocationRules.add("${it.pattern}:${it.newPattern}")
                relocate(it.pattern, it.newPattern)
            }
        }
        relocationsFile.writeText(relocationRules.joinToString("\n"))
    }
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
