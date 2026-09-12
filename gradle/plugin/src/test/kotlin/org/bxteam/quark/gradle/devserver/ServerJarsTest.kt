package org.bxteam.quark.gradle.devserver

import org.bxteam.quark.gradle.ServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ServerJarsTest {
    private val builds = listOf(
        ServerJars.Build("200", "u200", null, experimental = true),
        ServerJars.Build("199", "u199", null, experimental = false),
        ServerJars.Build("198", "u198", null, experimental = false),
    )

    @Test
    fun `normalizes build notations`() {
        assertEquals("latest", ServerJars.normalize("latest"))
        assertEquals("latest", ServerJars.normalize(" LATEST "))
        assertEquals("latest", ServerJars.normalize(""))
        assertEquals("196", ServerJars.normalize("#196"))
        assertEquals("196", ServerJars.normalize("196"))
    }

    @Test
    fun `latest skips experimental builds`() {
        assertEquals("199", ServerJars.select(builds, "latest")?.number)
        assertEquals("200", ServerJars.select(builds.take(1), "latest")?.number)
    }

    @Test
    fun `pinned build is selected by number`() {
        assertEquals("198", ServerJars.select(builds, "198")?.number)
        assertNull(ServerJars.select(builds, "1"))
    }

    @Test
    fun `offline fallback uses the newest cached or the pinned build`(@TempDir cache: File) {
        for (build in listOf("9", "10", "100")) {
            File(cache, "$build/paper-1.21.8.jar").apply { parentFile.mkdirs(); writeText(build) }
        }
        File(cache, "200").mkdirs() // interrupted download, no JAR

        assertEquals("100", ServerJars.cachedJar(cache, ServerType.PAPER, "1.21.8", "latest")?.readText())
        assertEquals("9", ServerJars.cachedJar(cache, ServerType.PAPER, "1.21.8", "9")?.readText())
        assertNull(ServerJars.cachedJar(cache, ServerType.PAPER, "1.21.8", "11"))
        assertNull(ServerJars.cachedJar(File(cache, "missing"), ServerType.PAPER, "1.21.8", "latest"))
    }
}
