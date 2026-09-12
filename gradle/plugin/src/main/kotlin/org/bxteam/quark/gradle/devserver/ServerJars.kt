package org.bxteam.quark.gradle.devserver

import org.bxteam.quark.gradle.ServerType
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.io.File

/**
 * Server JARs from the [MCJars](https://mcjars.app) API, cached per type, version and build in the Gradle user home.
 *
 * With [LATEST] every run asks MCJars for the newest build, so a new build is picked up as soon as it is published;
 * a build number pins the server to that build. Without network access the cached JAR is used.
 */
internal object ServerJars {
    const val LATEST = "latest"
    private const val API = "https://mcjars.app/api/v3"

    /** A build as listed by MCJars. */
    data class Build(val number: String, val jarUrl: String, val size: Long?, val experimental: Boolean)

    fun resolve(type: ServerType, version: String, build: String, cacheDirectory: File, logger: Logger): File {
        val wanted = normalize(build)
        val versionCache = File(cacheDirectory, "${type.name.lowercase()}/$version")

        val builds = try {
            fetchBuilds(type, version, wanted)
        } catch (e: HttpStatusException) {
            // 4xx is a wrong type, version or build; only server errors fall back to the cache
            if (e.status in 400..499) throw e
            return offline(versionCache, type, version, wanted, e, logger)
        } catch (e: GradleException) {
            throw e
        } catch (e: Exception) {
            return offline(versionCache, type, version, wanted, e, logger)
        }
        return download(builds, versionCache, type, version, wanted, logger)
    }

    private fun offline(versionCache: File, type: ServerType, version: String, wanted: String, cause: Exception, logger: Logger): File {
        val cached = cachedJar(versionCache, type, version, wanted)
            ?: throw GradleException("Cannot reach MCJars to download ${type.name} $version (${describe(wanted)}) and no build is cached", cause)
        logger.warn("Cannot reach MCJars (${cause.message}), using cached ${type.name} $version build ${cached.parentFile.name}")
        return cached
    }

    private fun download(builds: List<Build>, versionCache: File, type: ServerType, version: String, wanted: String, logger: Logger): File {
        val selected = select(builds, wanted)
            ?: throw GradleException("MCJars has no ${type.name} $version build $wanted" +
                if (builds.isEmpty()) "" else ". Found: " + builds.take(10).joinToString { it.number })

        val jar = jarFile(versionCache, type, version, selected.number)
        if (jar.isFile && (selected.size == null || jar.length() == selected.size)) {
            logger.lifecycle("Using ${type.name} $version build ${selected.number} (${describe(wanted)})")
        } else {
            val previous = if (wanted == LATEST) newestCachedBuild(versionCache) else null
            logger.lifecycle("Downloading ${type.name} $version build ${selected.number} (${describe(wanted)}" +
                (if (previous != null && previous != selected.number) ", previously $previous" else "") + ")...")
            Downloads.file(selected.jarUrl, jar)
            if (selected.size != null && jar.length() != selected.size) {
                jar.delete()
                throw GradleException("Downloaded ${type.name} $version build ${selected.number} has ${jar.length()} bytes, expected ${selected.size}")
            }
        }
        return jar
    }

    /** `"latest"`, `"196"` or `"#196"` → `"latest"` / `"196"`. */
    internal fun normalize(build: String): String {
        val trimmed = build.trim().removePrefix("#")
        return if (trimmed.isEmpty() || trimmed.equals(LATEST, ignoreCase = true)) LATEST else trimmed
    }

    /** Picks the wanted build; for [LATEST] the newest stable one, or the newest at all if every build is experimental. */
    internal fun select(builds: List<Build>, wanted: String): Build? =
        if (wanted == LATEST) builds.firstOrNull { !it.experimental } ?: builds.firstOrNull()
        else builds.firstOrNull { it.number == wanted }

    /** The cached JAR to fall back to when MCJars is unreachable. */
    internal fun cachedJar(versionCache: File, type: ServerType, version: String, wanted: String): File? {
        val number = if (wanted == LATEST) newestCachedBuild(versionCache) ?: return null else wanted
        return jarFile(versionCache, type, version, number).takeIf { it.isFile }
    }

    private fun newestCachedBuild(versionCache: File): String? = versionCache.listFiles()
        ?.filter { it.isDirectory && it.listFiles()?.any { file -> file.extension == "jar" } == true }
        ?.maxWithOrNull(compareBy<File>({ it.name.toLongOrNull() ?: -1 }, { it.lastModified() }))
        ?.name

    private fun jarFile(versionCache: File, type: ServerType, version: String, number: String) =
        File(versionCache, "${sanitize(number)}/${type.name.lowercase()}-$version.jar")

    /**
     * Builds of a version, newest first. For [LATEST] the first page is enough; a pinned build is looked up
     * with the API's search.
     */
    private fun fetchBuilds(type: ServerType, version: String, wanted: String): List<Build> {
        val search = if (wanted == LATEST) "" else "&search=${Downloads.encode(wanted)}"
        val response = try {
            Downloads.json("$API/builds/types/${type.apiName}/versions/${Downloads.encode(version)}?per_page=50$search").obj()
        } catch (e: HttpStatusException) {
            if (e.status == 404) throw GradleException("MCJars has no ${type.name} version $version" + availableVersions(type), e)
            throw e
        }
        return response["builds"].obj()["data"].arr().map { it.obj() }.mapNotNull { toBuild(type, it) }
    }

    /** A build is usable when its installation is plain downloads, one of which is the server JAR. */
    private fun toBuild(type: ServerType, build: Map<String, Any?>): Build? {
        val number = build["name"]?.toString()?.removePrefix("#") ?: return null
        val steps = build["installation"].arr().flatMap { it.arr() }.map { it.obj() }
        if (steps.any { it["type"] != "download" }) {
            throw GradleException("${type.name} build $number needs installation steps (${steps.map { it["type"] }.distinct()}) " +
                "that dev servers do not support")
        }
        val jar = steps.firstOrNull { it["file"]?.toString()?.endsWith(".jar") == true } ?: return null
        return Build(number, jar["url"].toString(), (jar["size"] as? Number)?.toLong(), build["experimental"] == true)
    }

    /** Java version MCJars lists for a version, or null if unknown. */
    fun requiredJava(type: ServerType, version: String): Int? = try {
        Downloads.json("$API/builds/types/${type.apiName}/versions?per_page=50&search=${Downloads.encode(version)}").obj()["versions"].obj()["data"].arr()
            .map { it.obj() }
            .firstOrNull { it["id"] == version }
            ?.get("java")
            ?.let { (it as? Number)?.toInt() }
    } catch (e: Exception) {
        null
    }

    private fun describe(wanted: String) = if (wanted == LATEST) "latest" else "pinned"

    private fun availableVersions(type: ServerType): String = try {
        val versions = Downloads.json("$API/builds/types/${type.apiName}/versions?per_page=20").obj()["versions"].obj()["data"].arr()
            .map { it.obj()["id"].toString() }
        if (versions.isEmpty()) "" else ". Newest versions: ${versions.joinToString()}"
    } catch (e: Exception) {
        ""
    }

    private fun sanitize(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}
