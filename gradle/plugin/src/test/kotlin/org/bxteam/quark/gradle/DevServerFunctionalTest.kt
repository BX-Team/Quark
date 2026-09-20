package org.bxteam.quark.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DevServerFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private val testRepository = File(System.getProperty("quark.testRepository")).toURI().toString()

    private fun project(quarkBlock: String, groovy: Boolean = false) {
        File(projectDir, if (groovy) "settings.gradle" else "settings.gradle.kts").writeText("rootProject.name = 'sample'".let {
            if (groovy) it else "rootProject.name = \"sample\""
        })
        if (groovy) {
            File(projectDir, "build.gradle").writeText(
                """
                plugins {
                    id 'java'
                    id 'com.gradleup.shadow'
                    id 'org.bxteam.quark'
                }
                group = 'com.example'
                repositories { maven { url = uri('$testRepository') } }
                $quarkBlock
                """.trimIndent()
            )
        } else {
            File(projectDir, "build.gradle.kts").writeText(
                """
                import org.bxteam.quark.gradle.QuarkModule
                import org.bxteam.quark.gradle.ServerPlatform
                import org.bxteam.quark.gradle.ServerType

                plugins {
                    java
                    id("com.gradleup.shadow")
                    id("org.bxteam.quark")
                }
                group = "com.example"
                repositories { maven(uri("$testRepository")) }
                $quarkBlock
                """.trimIndent()
            )
        }
    }

    private fun runner(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*args, "--stacktrace")

    @Test
    fun `no dev servers means no tasks`() {
        project("")

        val output = runner("tasks", "--all").build().output

        assertFalse(output.contains("runServer"), output)
    }

    @Test
    fun `default and named dev servers get their tasks`() {
        project(
            """
            quark {
                platform = ServerPlatform.PAPER
                devServer {
                    version = "1.21.8"
                    ram(4, GB)
                    acceptEula()
                    plugins {
                        modrinth("worldedit", "7.3.12")
                        jenkins("https://ci.athion.net", "FastAsyncWorldEdit", Regex("Bukkit"))
                    }
                }
                devServers {
                    register("legacy") { type = ServerType.SPIGOT; version = "1.8.8" }
                    register("proxy") { type = ServerType.VELOCITY; version = "3.4.0-SNAPSHOT" }
                }
            }
            """.trimIndent()
        )

        val output = runner("tasks", "--group", "quark").build().output

        assertTrue(output.contains("runServer - "), output)
        assertTrue(output.contains("runLegacyServer - "), output)
        assertTrue(output.contains("runProxyServer - "), output)
    }

    @Test
    fun `configuration cache is reused and configuration stays offline`() {
        project(
            """
            quark {
                platform = ServerPlatform.PAPER
                devServer {
                    version = "1.21.8"
                    plugins { url("https://example.invalid/never-fetched.jar") }
                }
            }
            """.trimIndent()
        )

        // --offline: any network access during configuration would fail the build
        val first = runner("help", "--task", "runServer", "--configuration-cache", "--offline").build()
        val second = runner("help", "--task", "runServer", "--configuration-cache", "--offline").build()

        assertTrue(first.output.contains("Configuration cache entry stored"), first.output)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    }

    @Test
    fun `missing platform and type fail when the task runs`() {
        project(
            """
            quark {
                devServer { version = "1.21.8" }
            }
            """.trimIndent()
        )
        File(projectDir, "src/main/java/com/example").mkdirs()
        File(projectDir, "src/main/java/com/example/Plugin.java").writeText("package com.example; public class Plugin {}")

        val result = runner("runServer", "--offline").buildAndFail()

        assertEquals(TaskOutcome.SUCCESS, result.task(":shadowJar")?.outcome, result.output)
        assertTrue(result.output.contains("set `quark { platform = ServerPlatform.PAPER }`"), result.output)
    }

    @Test
    fun `groovy dsl accepts the platform as a string`() {
        project(
            """
            quark {
                platform 'velocity'
                devServer {
                    version = '3.4.0-SNAPSHOT'
                    ram(512, MB)
                }
            }
            tasks.register('printType') {
                def type = tasks.named('runServer').flatMap { it.platform }
                doLast { println "platform=" + type.get() }
            }
            """.trimIndent(),
            groovy = true
        )

        val output = runner("printType").build().output

        assertTrue(output.contains("platform=VELOCITY"), output)
    }

    @Test
    fun `quark without shadow fails with a hint`() {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"api\"")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                java
                id("org.bxteam.quark")
            }
            """.trimIndent()
        )

        val output = runner("help").buildAndFail().output

        assertTrue(output.contains("requires the Shadow plugin"), output)
    }

    @Test
    fun `runserver shim applies quark and warns once`() {
        File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"legacy\"")
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                java
                id("com.gradleup.shadow")
                id("org.bxteam.runserver")
            }
            group = "com.example"
            repositories { maven(uri("$testRepository")) }
            quark { devServer { version = "1.21.8"; type = org.bxteam.quark.gradle.ServerType.PAPER } }
            """.trimIndent()
        )

        val output = runner("tasks", "--group", "quark").build().output

        assertEquals(1, Regex("'org.bxteam.runserver' plugin is deprecated").findAll(output).count(), output)
        assertTrue(output.contains(RunServerShimPlugin.MIGRATION_GUIDE), output)
        assertTrue(output.contains("runServer - "), output)
    }

    private fun runtimeQuarkModules(): Set<String> {
        val output = runner("dependencies", "--configuration", "runtimeClasspath", "--offline").build().output
        return Regex("""org\.bxteam\.quark:(quark-[a-z-]+)""").findAll(output).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `platform adds its adapter and modules add the rest`() {
        project(
            """
            quark {
                platform = ServerPlatform.PAPER
                modules(QuarkModule.DEPENDENCY, QuarkModule.UPDATE)
            }
            """.trimIndent()
        )

        assertEquals(
            setOf("quark-bom", "quark-paper", "quark-bukkit", "quark-platform-api", "quark-common", "quark-dependency", "quark-update"),
            runtimeQuarkModules()
        )
    }

    @Test
    fun `nothing but the BOM without platform and modules`() {
        project("")

        assertEquals(setOf("quark-bom"), runtimeQuarkModules())
    }

    @Test
    fun `groovy dsl accepts modules as strings`() {
        project(
            """
            quark {
                platform 'velocity'
                modules 'logger', 'dependency'
            }
            """.trimIndent(),
            groovy = true
        )

        assertEquals(
            setOf("quark-bom", "quark-velocity", "quark-platform-api", "quark-common", "quark-logger", "quark-dependency"),
            runtimeQuarkModules()
        )
    }

    @Test
    fun `config module adds the yaml format and the serializers of the platform`() {
        project(
            """
            quark {
                platform = ServerPlatform.PAPER
                modules(QuarkModule.CONFIG, QuarkModule.CONFIG_VALIDATOR)
            }
            """.trimIndent()
        )

        assertEquals(
            setOf(
                "quark-bom", "quark-paper", "quark-bukkit", "quark-platform-api", "quark-common",
                "quark-config", "quark-config-yaml", "quark-config-validator", "quark-config-serdes-bukkit"
            ),
            runtimeQuarkModules()
        )
    }

    @Test
    fun `config module without a bukkit platform adds no serializers`() {
        project(
            """
            quark {
                platform 'velocity'
                modules 'config', 'config-validator'
            }
            """.trimIndent(),
            groovy = true
        )

        assertEquals(
            setOf("quark-bom", "quark-velocity", "quark-platform-api", "quark-common", "quark-logger", "quark-config", "quark-config-yaml", "quark-config-validator"),
            runtimeQuarkModules()
        )
    }

    @Test
    fun `shadow jar relocates the snakeyaml-engine probe of the yaml format`() {
        project(
            """
            quark {
                modules(QuarkModule.CONFIG)
            }
            """.trimIndent()
        )

        runner("shadowJar", "--offline").build()

        val jar = File(projectDir, "build/libs").listFiles()!!.single { it.name.endsWith("-all.jar") }
        java.util.zip.ZipFile(jar).use { zip ->
            val entry = zip.getEntry("com/example/libs/quark/config/yaml/YamlFormat.class")
            requireNotNull(entry) { "YamlFormat was not relocated: ${zip.entries().toList().map { it.name }}" }
            val bytes = String(zip.getInputStream(entry).readBytes(), Charsets.ISO_8859_1)
            assertTrue(bytes.contains("com.example.libs.quark.snakeyaml.v2.api.Load"), "probe class literal must be relocated")
            assertTrue(bytes.contains("org{}snakeyaml{}engine"), "original package must survive relocation")
            assertFalse(bytes.contains("org/snakeyaml/engine"), "no class reference may be left unrelocated")
        }
    }
}
