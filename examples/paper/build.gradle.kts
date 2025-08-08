import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import org.bxteam.runserver.ServerType

plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.9"
    id("de.eldoria.plugin-yml.paper") version "0.7.1"
    id("org.bxteam.runserver") version "1.2.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT")

    // implementation("org.bxteam.quark:paper:1.0.0") // <-- uncomment in your project
    implementation(project(":quark-paper")) // don't use this line in your build file
}

val pluginName = "PaperExamplePlugin"
val packageName = "org.bxteam.example.paper"

paper {
    main = "$packageName.$pluginName"
    apiVersion = "1.21"
    author = "BX Team"
    name = pluginName
    version = "${project.version}"
    load = BukkitPluginDescription.PluginLoadOrder.STARTUP
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
        serverType(ServerType.PAPER)
        serverVersion("1.21.8")
        noGui(true)
        acceptMojangEula()
    }
}
