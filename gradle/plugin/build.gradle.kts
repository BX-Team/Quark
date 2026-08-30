plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.2.1"
}

// The plugin keeps the group it was first published with on the Gradle Plugin Portal.
group = "org.bxteam"
description = "Gradle plugin for Quark: runtime dependency manifest, relocations and Quark BOM"

dependencies {
    compileOnly("com.gradleup.shadow:shadow-gradle-plugin:9.1.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

// Version of the Quark libraries the plugin adds the BOM for: always the version it was built with.
val generateQuarkVersion = tasks.register("generateQuarkVersion") {
    val outputDir = layout.buildDirectory.dir("generated/quark-version")
    val quarkVersion = project.version.toString()
    inputs.property("version", quarkVersion)
    outputs.dir(outputDir)

    doLast {
        val file = outputDir.get().file("org/bxteam/quark/gradle/QuarkVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package org.bxteam.quark.gradle
            |
            |internal const val QUARK_VERSION: String = "$quarkVersion"
            |""".trimMargin()
        )
    }
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateQuarkVersion)
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            licenses {
                license {
                    name = "MIT License"
                    url = "https://opensource.org/licenses/MIT"
                }
            }
        }
    }
}

gradlePlugin {
    website = "https://bxteam.org/docs/quark"
    vcsUrl = "https://github.com/BX-Team/Quark"

    plugins {
        create("quark") {
            id = "org.bxteam.quark"
            displayName = "Quark Plugin"
            description = "Runtime dependency manifest, relocations and Quark BOM for Minecraft server plugins"
            implementationClass = "org.bxteam.quark.gradle.QuarkPlugin"
            tags = listOf("maven", "downloader", "runtime dependency", "minecraft", "bukkit", "spigot", "paper", "velocity")
        }
    }
}
