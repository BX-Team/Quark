plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.9"
    id("de.eldoria.plugin-yml.bukkit") version "0.7.1"
}

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
}

dependencies {
    compileOnly("net.md-5:bungeecord-api:1.19-R0.1-SNAPSHOT")

    // implementation("org.bxteam.quark:bungee:1.0.0") // <-- uncomment in your project
    implementation(project(":quark-bungee")) // don't use this line in your build file
}

val pluginName = "BungeeExamplePlugin"
val packageName = "org.bxteam.example.bungee"

bukkit {
    main = "$packageName.$pluginName"
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
