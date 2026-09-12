package org.bxteam.quark.gradle.devserver

import org.bxteam.quark.gradle.ServerType

/**
 * Offline knowledge about Minecraft versions, so nothing has to be fetched during configuration.
 */
internal object MinecraftVersions {
    private fun parts(version: String): List<Int> =
        version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    /** Compares dotted versions numerically: `1.21` > `1.15.2`, `26.1` > `1.21.8`. */
    fun compare(left: String, right: String): Int {
        val a = parts(left)
        val b = parts(right)
        for (i in 0 until maxOf(a.size, b.size)) {
            val result = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
            if (result != 0) return result
        }
        return 0
    }

    /** Whether the server accepts `--nogui` (Minecraft 1.15.2+). */
    fun supportsNoGui(type: ServerType, version: String): Boolean = !type.proxy && compare(version, "1.15.2") >= 0

    /** Java version a server needs. Proxies run on Java 21, the current baseline of Velocity and BungeeCord. */
    fun requiredJava(type: ServerType, version: String): Int = when {
        type.proxy -> 21
        compare(version, "26") >= 0 -> 25
        compare(version, "1.20.5") >= 0 -> 21
        compare(version, "1.17") >= 0 -> 17
        else -> 8
    }
}
