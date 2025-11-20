plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.0.0"
}

group = "org.bxteam"
version = "1.2.1"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly("com.gradleup.shadow:com.gradleup.shadow.gradle.plugin:9.1.0")
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        resources.setSrcDirs(emptyList<String>())
    }
    test {
        java.setSrcDirs(emptyList<String>())
        resources.setSrcDirs(emptyList<String>())
    }
}

gradlePlugin {
    plugins {
        create("quark") {
            id = "org.bxteam.quark"
            displayName = "Quark Plugin"
            description = "A lightweight, runtime dependency management system for plugins running on Minecraft server platforms"
            implementationClass = "org.bxteam.quark.gradle.QuarkPlugin"
            website = "https://bxteam.org/docs/quark"
            vcsUrl = "https://github.com/BX-Team/Quark"
            tags = listOf("maven", "downloader", "runtime dependency", "minecraft", "bukkit", "spigot", "paper", "bungee", "velocity")
        }
    }
}
