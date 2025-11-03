plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
    id("de.eldoria.plugin-yml.bukkit") version "0.7.1"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")

    // implementation("org.bxteam.quark:bukkit:1.0.0") // <-- uncomment in your project
    implementation(project(":quark-bukkit")) // don't use this line in your build file
}

val pluginName = "BukkitExamplePlugin"
val packageName = "org.bxteam.example.bukkit"

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

    runServer {
        minecraftVersion("1.21.8")
        allJvmArgs = listOf("-DPaper.IgnoreJavaVersion=true")
    }
}
