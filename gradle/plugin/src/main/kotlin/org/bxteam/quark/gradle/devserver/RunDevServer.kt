package org.bxteam.quark.gradle.devserver

import org.bxteam.quark.gradle.ServerPlatform
import org.bxteam.quark.gradle.ServerType
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Runs a dev server with the project's plugin. Registered by the Quark plugin for every entry of
 * `quark.devServers`; everything that needs the network happens here, in the task action.
 */
@DisableCachingByDefault(because = "Runs an interactive server")
abstract class RunDevServer : JavaExec() {
    /** Name of the dev server entry. */
    @get:Internal
    abstract val serverName: Property<String>

    /** Server type set on the dev server entry. */
    @get:Input
    @get:Optional
    abstract val serverType: Property<ServerType>

    /** `quark.platform`, used when [serverType] is not set. */
    @get:Input
    @get:Optional
    abstract val platform: Property<ServerPlatform>

    /** Minecraft or proxy version. */
    @get:Input
    @get:Optional
    abstract val serverVersion: Property<String>

    /** Server build, `latest` or a build number. */
    @get:Input
    abstract val serverBuild: Property<String>

    /** Maximum heap. */
    @get:Input
    abstract val maxMemory: Property<String>

    /** Whether `--nogui` is passed. */
    @get:Input
    abstract val noGui: Property<Boolean>

    /** Whether the EULA is accepted. */
    @get:Input
    abstract val acceptEula: Property<Boolean>

    /** Plugins installed next to the project's plugin. */
    @get:Input
    abstract val pluginSources: ListProperty<PluginSource>

    /** The project's plugin JAR. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val pluginJar: RegularFileProperty

    /** Directory the server runs in. */
    @get:Internal
    abstract val runDirectory: DirectoryProperty

    /** Shared cache of downloaded server JARs. */
    @get:Internal
    abstract val serverJarCache: DirectoryProperty

    /** `quark` runtime dependencies to place into the plugin's library directory. */
    @get:Internal
    abstract val quarkLibraries: ListProperty<DevLibrary>

    init {
        group = "quark"
        outputs.upToDateWhen { false }
    }

    override fun exec() {
        val type = resolveType()
        val version = serverVersion.orNull
            ?: throw GradleException("Dev server '${serverName.get()}' has no version. Set it with `version = \"1.21.8\"`")

        val source = if (serverType.isPresent) "set on dev server '${serverName.get()}'" else "from quark.platform = ${platform.get()}"
        logger.lifecycle("Dev server '${serverName.get()}': ${type.name} $version ($source)")

        val runDir = runDirectory.get().asFile
        val pluginsDir = File(runDir, "plugins")
        pluginsDir.mkdirs()

        val jar = pluginJar.get().asFile
        jar.copyTo(File(pluginsDir, jar.name), overwrite = true)
        PluginInstaller(pluginsDir, type, logger).install(pluginSources.get())
        LibraryPreloader.preload(jar, pluginsDir, quarkLibraries.get(), logger)

        checkJavaVersion(type, version)
        val serverJar = ServerJars.resolve(type, version, serverBuild.get(), serverJarCache.get().asFile, logger)

        if (acceptEula.get()) {
            File(runDir, "eula.txt").writeText("eula=true\n")
            jvmArgs("-Dcom.mojang.eula.agree=true")
        }
        if (type == ServerType.SPIGOT) {
            jvmArgs("-DIReallyKnowWhatIAmDoingISwear")
        }
        jvmArgs("-Xmx${maxMemory.get()}", "-Dfile.encoding=UTF-8")
        if (noGui.get() && MinecraftVersions.supportsNoGui(type, version)) {
            args("--nogui")
        }

        classpath = objectFactory.fileCollection().from(serverJar)
        workingDir = runDir
        standardInput = System.`in`

        logger.lifecycle("Starting ${type.name} $version in ${runDir.path} with Java ${javaLauncher.get().metadata.languageVersion}")
        super.exec()
    }

    /** The Java version is chosen offline during configuration; MCJars knows the real requirement. */
    private fun checkJavaVersion(type: ServerType, version: String) {
        val required = ServerJars.requiredJava(type, version) ?: return
        val actual = javaLauncher.get().metadata.languageVersion.asInt()
        if (actual < required) {
            throw GradleException("${type.name} $version needs Java $required, the dev server would run on Java $actual. " +
                "Set `javaVersion = $required` on dev server '${serverName.get()}'")
        }
    }

    private fun resolveType(): ServerType = serverType.orNull
        ?: platform.orNull?.defaultServerType
        ?: throw GradleException(
            "Cannot tell which server dev server '${serverName.get()}' should run: set `quark { platform = ServerPlatform.PAPER }` " +
                "or `type = ServerType.PAPER` on the dev server"
        )
}
