package org.bxteam.quark.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import javax.tools.ToolProvider

class RelocateFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    private val testRepository = File(System.getProperty("quark.testRepository")).toURI().toString()

    /**
     * Publishes `com.acme:greeter:1.0` (packages `com.acme.greeter` and `com.acme.greeter.internal`) and
     * `com.acme:other:1.0` to a Maven repository in the project.
     */
    private fun publishLibraries() {
        val sources = File(projectDir, "lib-src").apply { mkdirs() }
        val greeter = File(sources, "com/acme/greeter/Greeter.java")
        greeter.parentFile.mkdirs()
        greeter.writeText("package com.acme.greeter; public class Greeter { public static String greet() { return com.acme.greeter.internal.Names.name(); } }")
        val names = File(sources, "com/acme/greeter/internal/Names.java")
        names.parentFile.mkdirs()
        names.writeText("package com.acme.greeter.internal; public class Names { public static String name() { return \"quark\"; } }")
        val other = File(sources, "com/acme/other/Other.java")
        other.parentFile.mkdirs()
        other.writeText("package com.acme.other; public class Other { }")

        val classes = File(projectDir, "lib-classes")
        val compiler = ToolProvider.getSystemJavaCompiler()
        assertEquals(0, compiler.run(null, null, null, "--release", "17", "-d", classes.path, greeter.path, names.path, other.path))

        publish("greeter", classes, "com/acme/greeter/")
        publish("other", classes, "com/acme/other/")
    }

    private fun publish(artifact: String, classes: File, prefix: String) {
        val dir = File(projectDir, "repo/com/acme/$artifact/1.0").apply { mkdirs() }
        File(dir, "$artifact-1.0.pom").writeText(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.acme</groupId><artifactId>$artifact</artifactId><version>1.0</version>
            </project>
            """.trimIndent()
        )
        JarOutputStream(File(dir, "$artifact-1.0.jar").outputStream()).use { jar ->
            classes.walkTopDown().filter { it.isFile }.forEach { file ->
                val name = file.relativeTo(classes).invariantSeparatorsPath
                if (name.startsWith(prefix)) {
                    jar.putNextEntry(JarEntry(name))
                    jar.write(file.readBytes())
                    jar.closeEntry()
                }
            }
        }
    }

    private fun project(dependencies: String, groovy: Boolean = false) {
        publishLibraries()
        val main = File(projectDir, "src/main/java/com/example/Main.java")
        main.parentFile.mkdirs()
        main.writeText("package com.example; public class Main { public static String run() { return com.acme.greeter.Greeter.greet() + com.acme.other.Other.class.getName(); } }")

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
                repositories {
                    maven { url = uri('$testRepository') }
                    maven { url = uri('repo') }
                }
                dependencies {
                    $dependencies
                }
                """.trimIndent()
            )
        } else {
            File(projectDir, "settings.gradle.kts").writeText("rootProject.name = \"sample\"")
            File(projectDir, "build.gradle.kts").writeText(
                """
                import org.bxteam.quark.gradle.relocate

                plugins {
                    java
                    id("com.gradleup.shadow")
                    id("org.bxteam.quark")
                }
                group = "com.example"
                repositories {
                    maven(uri("$testRepository"))
                    maven(uri("repo"))
                }
                dependencies {
                    $dependencies
                }
                """.trimIndent()
            )
        }
    }

    private fun runner(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*args, "--stacktrace")

    private fun shadowJarText(entry: String): String {
        val jar = File(projectDir, "build/libs").listFiles()!!.single { it.name.endsWith("-all.jar") }
        return ZipFile(jar).use { zip ->
            String(zip.getInputStream(requireNotNull(zip.getEntry(entry)) { "missing $entry" }).readBytes(), Charsets.ISO_8859_1)
        }
    }

    @Test
    fun `relocate moves the library packages in the shaded jar and the manifest`() {
        project(
            """
            quark("com.acme:greeter:1.0") { relocate = true }
            quark("com.acme:other:1.0")
            """.trimIndent()
        )

        runner("shadowJar", "--configuration-cache").build()

        val manifest = shadowJarText("META-INF/quark/manifest")
        assertTrue(manifest.contains("com.acme.greeter=com.example.libs.com.acme.greeter\n"), manifest)
        assertFalse(manifest.contains("com.acme.greeter.internal="), "subpackages are covered by their root: $manifest")
        assertFalse(manifest.contains("com.acme.other="), manifest)

        val main = shadowJarText("com/example/Main.class")
        assertTrue(main.contains("com/example/libs/com/acme/greeter/Greeter"), "references to the library are relocated")
        assertTrue(main.contains("com/acme/other/Other"), "other libraries are left alone")

        // reused from the configuration cache
        val second = runner("shadowJar", "--configuration-cache").build()
        assertTrue(second.output.contains("Configuration cache entry reused"), second.output)
    }

    @Test
    fun `groovy dsl marks dependencies with ext`() {
        project("quark('com.acme:greeter:1.0') { ext.relocate = true }", groovy = true)

        runner("collectRelocatedQuarkLibraries").build()

        assertEquals(
            "com.acme.greeter=com.example.libs.com.acme.greeter\n",
            File(projectDir, "build/quark/relocated-libraries.txt").readText()
        )
    }

    @Test
    fun `root packages skip nested packages`() {
        publishLibraries()

        assertEquals(
            listOf("com.acme.greeter"),
            CollectRelocatedLibraries.rootPackages(File(projectDir, "repo/com/acme/greeter/1.0/greeter-1.0.jar"))
        )
    }
}
