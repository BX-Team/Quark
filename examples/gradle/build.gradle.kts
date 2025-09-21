plugins {
    id("java-library")
    id("com.gradleup.shadow") version "8.3.9"
    id("de.eldoria.plugin-yml.bukkit") version "0.7.1"
    id("org.bxteam.quark") // version "1.x.x" // <-- uncomment in your project and set the version
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
    quark("com.google.code.gson:gson:2.10.1") // example of a dependency that will be downloaded by quark
}

quark {
    platform = "bukkit" // Specify the platform that will be used in your project (bukkit, paper, velocity or bungee)

    repositories {
        includeProjectRepositories()
    }
}

val pluginName = "QuarkGradleExamplePlugin"
val packageName = "org.bxteam.example.gradle"

bukkit {
    main = "$packageName.$pluginName"
    apiVersion = "1.21"
    author = "BX Team"
    name = pluginName
    version = "${project.version}"
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    }

    jar {
        enabled = false
    }

    compileJava {
        options.release.set(17)
    }

    shadowJar {
        archiveBaseName.set(pluginName)
        archiveClassifier.set("")
        minimize()
    }
}
