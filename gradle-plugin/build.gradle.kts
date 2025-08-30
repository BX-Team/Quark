plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "org.bxteam"
version = "1.1.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    compileOnly("com.github.johnrengelman.shadow:com.github.johnrengelman.shadow.gradle.plugin:8.1.1")
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
