plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.2.1"
}

group = "org.bxteam"
description = "Gradle plugin for Quark: runtime dependency manifest, relocations and Quark BOM"

val testShadow = configurations.create("testShadow")

dependencies {
    compileOnly("com.gradleup.shadow:shadow-gradle-plugin:9.1.0")
    testShadow("com.gradleup.shadow:shadow-gradle-plugin:9.1.0")

    testImplementation(gradleTestKit())
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(testShadow)
}

tasks.test {
    useJUnitPlatform()
    dependsOn(
        listOf(
            "bom", "common", "platform-api", "dependency", "logger", "update", "bukkit", "bungee", "paper", "velocity",
            "config", "config-yaml", "config-validator", "config-serdes-bukkit"
        )
            .map { ":quark-$it:publishMavenPublicationToBuildLocalRepository" }
    )
    systemProperty("quark.testRepository", rootProject.layout.buildDirectory.dir("local-repo").get().asFile.absolutePath)
    maxParallelForks = 1
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

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
    repositories {
        maven {
            name = "buildLocal"
            url = rootProject.layout.buildDirectory.dir("local-repo").get().asFile.toURI()
        }
    }
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
        // run-server-plugin moved into Quark; 2.x of its id only applies org.bxteam.quark and points to the migration guide
        create("runServer") {
            id = "org.bxteam.runserver"
            displayName = "RunServer (deprecated, use org.bxteam.quark)"
            description = "Deprecated: run-server-plugin moved into the Quark Gradle plugin (org.bxteam.quark)"
            implementationClass = "org.bxteam.quark.gradle.RunServerShimPlugin"
            tags = listOf("minecraft", "server", "run", "bxteam", "paper", "velocity")
        }
    }
}
