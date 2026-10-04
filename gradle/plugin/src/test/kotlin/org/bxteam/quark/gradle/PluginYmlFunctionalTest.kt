package org.bxteam.quark.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PluginYmlFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private val testRepository = File(System.getProperty("quark.testRepository")).toURI().toString()

    private fun project(quarkBlock: String, groovy: Boolean = false) {
        if (groovy) {
            File(projectDir, "settings.gradle").writeText("rootProject.name = 'sample'")
            File(projectDir, "build.gradle").writeText(
                """
                plugins {
                    id 'java'
                    id 'com.gradleup.shadow'
                    id 'org.bxteam.quark'
                }
                group = 'com.example'
                version = '1.0.0'
                repositories { maven { url = uri('$testRepository') } }
                $quarkBlock
                """.trimIndent()
            )
        } else {
            File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"sample\"")
            File(projectDir, "build.gradle.kts").writeText(
                """
                import org.bxteam.quark.gradle.ServerPlatform

                plugins {
                    java
                    id("com.gradleup.shadow")
                    id("org.bxteam.quark")
                }
                group = "com.example"
                version = "1.0.0"
                description = "Says hello"
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

    private fun generated(name: String) = File(projectDir, "build/resources/main/$name")

    @Test
    fun `plugin yml for paper with commands and permissions`() {
        project(
            """
            quark {
                platform = ServerPlatform.FOLIA
                pluginYml {
                    main = "com.example.Sample"
                    apiVersion = "1.20"
                    authors("Me", "You")
                    website = "https://example.com"
                    softDepend("PlaceholderAPI")
                    commands {
                        register("hello") {
                            aliases("hi")
                            usage = "/<command> [player]"
                        }
                        register("bye")
                    }
                    permissions {
                        register("sample.hello") { default = TRUE }
                        register("sample.admin") {
                            default = OP
                            children("sample.hello")
                        }
                    }
                    extra("paper-skip-libraries", true)
                }
            }
            """.trimIndent()
        )

        runner("processResources").build()

        assertEquals(
            """
            name: sample
            version: "1.0.0"
            main: com.example.Sample
            description: Says hello
            api-version: "1.20"
            authors:
              - Me
              - You
            website: "https://example.com"
            softdepend:
              - PlaceholderAPI
            folia-supported: true
            commands:
              bye: {}
              hello:
                aliases:
                  - hi
                usage: "/<command> [player]"
            permissions:
              sample.admin:
                default: op
                children:
                  sample.hello: true
              sample.hello:
                default: true
            paper-skip-libraries: true

            """.trimIndent(),
            generated("plugin.yml").readText()
        )
    }

    @Test
    fun `paper plugin translates dependencies and rejects commands`() {
        project(
            """
            quark {
                platform = ServerPlatform.PAPER
                pluginYml {
                    paperPlugin()
                    main = "com.example.Sample"
                    apiVersion = "1.21"
                    depend("Vault")
                    softDepend("PlaceholderAPI")
                    loadBefore("Essentials")
                }
            }
            """.trimIndent()
        )

        runner("processResources").build()

        val text = generated("paper-plugin.yml").readText()
        assertFalse(generated("plugin.yml").exists())
        assertTrue(
            text.contains(
                """
                dependencies:
                  server:
                    Vault:
                      load: BEFORE
                      required: true
                    PlaceholderAPI:
                      load: BEFORE
                      required: false
                    Essentials:
                      load: AFTER
                      required: false
                """.trimIndent()
            ),
            text
        )

        File(projectDir, "build.gradle.kts").appendText("\nquark { pluginYml { commands { register(\"hello\") } } }\n")
        val output = runner("processResources").buildAndFail().output
        assertTrue(output.contains("register them in code with LifecycleEvents.COMMANDS"), output)
    }

    @Test
    fun `velocity json from the groovy dsl`() {
        project(
            """
            quark {
                platform 'velocity'
                pluginYml {
                    name = 'Sample Proxy'
                    main = 'com.example.Sample'
                    authors 'Me'
                    depend 'luckperms'
                    softDepend 'tab'
                }
            }
            """.trimIndent(),
            groovy = true
        )

        runner("processResources").build()

        assertEquals(
            """
            {
              "id": "sample-proxy",
              "name": "Sample Proxy",
              "version": "1.0.0",
              "authors": [
                "Me"
              ],
              "dependencies": [
                {
                  "id": "luckperms",
                  "optional": false
                },
                {
                  "id": "tab",
                  "optional": true
                }
              ],
              "main": "com.example.Sample"
            }

            """.trimIndent(),
            generated("velocity-plugin.json").readText()
        )
    }

    @Test
    fun `bungee yml leaves out what bungee does not read and rejects what changes loading`() {
        project(
            """
            quark {
                platform = ServerPlatform.BUNGEE
                pluginYml {
                    main = "com.example.Sample"
                    website = "https://example.com"
                    authors("Me", "You")
                    depend("LuckPerms")
                }
            }
            """.trimIndent()
        )

        runner("processResources").build()

        assertEquals(
            """
            name: sample
            main: com.example.Sample
            version: "1.0.0"
            author: "Me, You"
            depends:
              - LuckPerms
            description: Says hello

            """.trimIndent(),
            generated("bungee.yml").readText()
        )

        File(projectDir, "build.gradle.kts").appendText("\nquark { pluginYml { loadBefore(\"Other\") } }\n")
        val output = runner("processResources").buildAndFail().output
        assertTrue(output.contains("loadBefore is not supported in bungee.yml"), output)
    }

    @Test
    fun `without the block nothing is generated and a hand-written file is used`() {
        project("quark { platform = ServerPlatform.PAPER }")
        File(projectDir, "src/main/resources").mkdirs()
        File(projectDir, "src/main/resources/plugin.yml").writeText("name: Manual\n")

        runner("processResources").build()

        assertEquals("name: Manual\n", generated("plugin.yml").readText())
    }

    @Test
    fun `a hand-written file next to the block fails with a hint`() {
        project(
            """
            quark {
                platform = ServerPlatform.PAPER
                pluginYml { main = "com.example.Sample" }
            }
            """.trimIndent()
        )
        File(projectDir, "src/main/resources").mkdirs()
        File(projectDir, "src/main/resources/plugin.yml").writeText("name: Manual\n")

        val output = runner("processResources").buildAndFail().output

        assertTrue(output.contains("generates plugin.yml, but"), output)
        assertTrue(output.contains("Delete that file or remove pluginYml { }"), output)
    }

    @Test
    fun `missing main class fails with a hint`() {
        project(
            """
            quark {
                platform = ServerPlatform.PAPER
                pluginYml { }
            }
            """.trimIndent()
        )

        val output = runner("processResources").buildAndFail().output

        assertTrue(output.contains("set the main class: `pluginYml { main = \"com.example.MyPlugin\" }`"), output)
    }

    @Test
    fun `configuration cache is reused`() {
        project(
            """
            quark {
                platform = ServerPlatform.PAPER
                pluginYml {
                    main = "com.example.Sample"
                    permissions { register("sample.use") }
                }
            }
            """.trimIndent()
        )

        val first = runner("processResources", "--configuration-cache").build()
        val second = runner("processResources", "--configuration-cache").build()

        assertTrue(first.output.contains("Configuration cache entry stored"), first.output)
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
        assertTrue(generated("plugin.yml").readText().contains("sample.use: {}"))
    }
}
