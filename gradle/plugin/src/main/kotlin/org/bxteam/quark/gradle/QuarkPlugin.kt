package org.bxteam.quark.gradle

import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.bxteam.quark.gradle.devserver.DevLibrary
import org.bxteam.quark.gradle.devserver.DevServerSpec
import org.bxteam.quark.gradle.devserver.MinecraftVersions
import org.bxteam.quark.gradle.devserver.RunDevServer
import org.bxteam.quark.gradle.pluginyml.GeneratePluginYml
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

/**
 * The `org.bxteam.quark` plugin.
 *
 * - adds the `quark` configuration for dependencies downloaded at runtime and writes them, with repositories
 *   and relocations, to the `META-INF/quark/manifest` resource;
 * - adds `repo.bxteam.org`, the Quark BOM, the adapter of `quark.platform` and the modules listed in
 *   `quark.modules`;
 * - relocates Quark itself to `<group>.libs.quark` in the shaded JAR (snakeyaml-engine, used by `quark-config-yaml`,
 *   to `<group>.libs.quark.snakeyaml`), plus your `relocate(...)` rules;
 * - generates `plugin.yml` and the other plugin descriptors from `quark { pluginYml { } }`.
 */
class QuarkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("quark", QuarkExtension::class.java).apply {
            repositories.convention(listOf(GOOGLE_MAVEN_CENTRAL_MIRROR))
            includeProjectRepositories.convention(false)
            relocations.convention(emptyList())
            modules.convention(emptySet())
            relocateQuark.convention(true)
            quarkPackage.convention(project.provider { defaultQuarkPackage(project) })
            librariesPackage.convention(project.provider { "${requireGroup(project, "librariesPackage = \"my.plugin.libs\"")}.libs" })
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
            addQuarkModules(project, extension)

            val collectRelocated = project.tasks.register<CollectRelocatedLibraries>("collectRelocatedQuarkLibraries") {
                group = "build"
                description = "Finds the packages of the quark libraries marked with relocate = true"
                modules.set(project.provider {
                    quark.get().dependencies.withType(ModuleDependency::class.java)
                        .filter { it.relocate }
                        .map { "${it.group}:${it.name}" }
                        .toSet()
                })
                librariesPackage.set(extension.librariesPackage)
                artifacts.set(quark.flatMap { configuration ->
                    configuration.incoming.artifacts.resolvedArtifacts.map { resolved ->
                        resolved.mapNotNull { artifact ->
                            val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: return@mapNotNull null
                            LibraryArtifact("${id.group}:${id.module}", artifact.file)
                        }
                    }
                })
                classpath.from(quark)
                outputFile.set(project.layout.buildDirectory.file("quark/relocated-libraries.txt"))
            }
            val relocatedLibraries = collectRelocated.flatMap { it.outputFile }.map { CollectRelocatedLibraries.read(it.asFile) }

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
                relocations.set(extension.relocations.zip(relocatedLibraries) { configured, libraries ->
                    (configured + libraries).map { "${it.pattern}=${it.newPattern}" }
                })
                dependsOn(collectRelocated)
                outputDirectory.set(project.layout.buildDirectory.dir("generated/quark/resources"))
            }

            val generatePluginYml = registerPluginYml(project, extension)

            project.extensions.getByType(SourceSetContainer::class.java).named("main") {
                resources.srcDir(generateManifest.map { it.outputDirectory })
                resources.srcDir(generatePluginYml.map { it.outputDirectory })
            }

            registerDevServers(project, extension, quark)

            project.tasks.withType<ShadowJar>().configureEach {
                dependsOn(collectRelocated)
                relocators.addAll(relocatedLibraries.map { rules -> rules.map { SimpleRelocator(it.pattern, it.newPattern) } })
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

            if (extension.pluginYml.enabled.get()) {
                PLUGIN_YML_PLUGIN_IDS.firstOrNull { plugins.hasPlugin(it) }?.let {
                    throw GradleException(
                        "quark { pluginYml { } } and the '$it' plugin both generate the plugin descriptor. " +
                            "Remove id(\"$it\") and its block, or remove pluginYml { }."
                    )
                }
            }
        }
    }

    private fun registerPluginYml(project: Project, extension: QuarkExtension): TaskProvider<GeneratePluginYml> {
        extension.pluginYml.apply {
            name.convention(project.rootProject.name)
            version.convention(project.provider { project.version.toString() })
            description.convention(project.provider { project.description })
            foliaSupported.convention(extension.platform.map { it == ServerPlatform.FOLIA })
            paperPlugin.convention(false)
        }

        val generatedResources = project.layout.buildDirectory.dir("generated/quark")
        val resourceDirectories = project.provider {
            val generated = generatedResources.get().asFile
            project.extensions.getByType(SourceSetContainer::class.java).getByName("main").resources.srcDirs
                .filterNot { it.startsWith(generated) }
        }

        return project.tasks.register<GeneratePluginYml>("generatePluginYml") {
            group = "build"
            description = "Generates plugin.yml, paper-plugin.yml, bungee.yml or velocity-plugin.json from quark { pluginYml { } }"
            descriptor.set(project.provider {
                if (extension.pluginYml.enabled.get()) extension.pluginYml.snapshot(extension.platform.orNull) else null
            })
            handWritten.from(resourceDirectories.map { directories ->
                directories.flatMap { directory -> GeneratePluginYml.FILE_NAMES.map { directory.resolve(it) } }
            })
            outputDirectory.set(generatedResources.map { it.dir("plugin-yml") })
        }
    }

    /**
     * The adapter of `quark.platform` plus `quark.modules` (and the platform serializers with the config module),
     * added lazily so the `quark { }` block can come after the plugins block. Versions come from the BOM.
     */
    private fun addQuarkModules(project: Project, extension: QuarkExtension) {
        val platform = extension.platform.map { listOf(it) }.orElse(emptyList())
        val artifacts = platform.zip(extension.modules) { platforms, modules ->
            val serdes = if (QuarkModule.CONFIG in modules) platforms.mapNotNull { it.serdesArtifactId } else emptyList()
            platforms.map { it.adapterArtifactId } + modules.sortedBy { it.ordinal }.flatMap { it.artifactIds.toList() } + serdes
        }

        project.configurations.named(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME) {
            dependencies.addAllLater(artifacts.map { ids -> ids.map { project.dependencies.create("org.bxteam.quark:$it") } })
        }
    }

    private fun registerDevServers(project: Project, extension: QuarkExtension, quark: org.gradle.api.NamedDomainObjectProvider<org.gradle.api.artifacts.Configuration>) {
        val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
        val serverJarCache = project.gradle.gradleUserHomeDir.resolve("caches/quark/dev-servers")
        val libraries = quark.flatMap { configuration ->
            configuration.incoming.artifacts.resolvedArtifacts.map { artifacts ->
                artifacts.mapNotNull { artifact ->
                    val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier ?: return@mapNotNull null
                    DevLibrary(id.group, id.module, id.version, artifact.file)
                }
            }
        }

        extension.devServers.all {
            val spec = this
            spec.noGui.convention(true)
            spec.acceptEula.convention(false)
            spec.maxMemory.convention("2G")
            spec.build.convention("latest")
            spec.perVersionFolder.convention(false)
            spec.runDirectory.convention(project.layout.projectDirectory.dir(project.provider {
                val type = spec.type.orNull ?: extension.platform.orNull?.defaultServerType
                when {
                    spec.name != DevServerSpec.DEFAULT -> "run/${spec.name}"
                    spec.perVersionFolder.get() -> "run/${spec.version.getOrElse("unknown")}/${type?.name?.lowercase() ?: "server"}"
                    else -> "run/${type?.name?.lowercase() ?: "server"}"
                }
            }))
            spec.inputJar.convention(project.layout.file(project.provider {
                project.tasks.withType(ShadowJar::class.java).findByName("shadowJar")?.archiveFile?.get()?.asFile
            }))

            project.tasks.register(spec.taskName, RunDevServer::class.java) {
                description = "Runs the dev server '${spec.name}' with this plugin"
                serverName.set(spec.name)
                serverType.set(spec.type)
                platform.set(extension.platform)
                serverVersion.set(spec.version)
                serverBuild.set(spec.build)
                maxMemory.set(spec.maxMemory)
                noGui.set(spec.noGui)
                acceptEula.set(spec.acceptEula)
                pluginSources.set(spec.pluginSources)
                pluginJar.set(spec.inputJar)
                runDirectory.set(spec.runDirectory)
                this.serverJarCache.set(serverJarCache)
                quarkLibraries.set(libraries)

                // the Shadow JAR quark relocates is what the server must load, not the plain jar
                dependsOn(project.tasks.withType(ShadowJar::class.java).matching { it.name == "shadowJar" })

                val javaVersion = spec.javaVersion.orElse(spec.version.map { version ->
                    val type = spec.type.orNull ?: extension.platform.orNull?.defaultServerType ?: ServerType.PAPER
                    MinecraftVersions.requiredJava(type, version)
                })
                javaLauncher.set(toolchains.launcherFor {
                    languageVersion.set(javaVersion.map { JavaLanguageVersion.of(it) })
                })
            }
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
                // quark-config-yaml downloads snakeyaml-engine at runtime into the package it was relocated to
                relocate(SNAKEYAML_ENGINE_PACKAGE, "$quarkPackage.snakeyaml")
            }
            relocations.forEach { relocate(it.pattern, it.newPattern) }
        }
    }

    private fun defaultQuarkPackage(project: Project): String =
        "${requireGroup(project, "quarkPackage = \"my.plugin.libs.quark\"")}.libs.quark"

    private fun requireGroup(project: Project, alternative: String): String {
        val group = project.group.toString()
        if (group.isBlank()) {
            throw GradleException(
                "Cannot relocate Quark: the project has no group. Set `group = \"...\"` or `quark { $alternative }`"
            )
        }
        return group
    }

    private companion object {
        const val CONFIGURATION_NAME = "quark"
        const val SHADOW_PLUGIN_ID = "com.gradleup.shadow"
        const val QUARK_PACKAGE = "org.bxteam.quark"
        const val SNAKEYAML_ENGINE_PACKAGE = "org.snakeyaml.engine"
        val PLUGIN_YML_PLUGIN_IDS = listOf("bukkit", "paper", "bungee", "nukkit")
            .flatMap { listOf("net.minecrell.plugin-yml.$it", "de.eldoria.plugin-yml.$it") }
    }
}

/**
 * The Quark extension of this project.
 */
val Project.quark: QuarkExtension
    get() = extensions.getByType(QuarkExtension::class.java)
