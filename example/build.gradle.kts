import org.bxteam.runserver.ServerType

plugins {
    id("org.bxteam.runserver") version "1.2.1"
    id("com.gradleup.shadow") version "8.3.8"
}

repositories {
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
}

dependencies {
    implementation(project(":quark-bukkit"))

    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks {
    shadowJar {
        archiveBaseName.set("quark-bukkit")
        archiveClassifier.set("")
        archiveVersion.set("1.0.0")
        minimize()
    }

    runServer {
        serverType(ServerType.PAPER)
        serverVersion("1.21.8")
        noGui(true)
        acceptMojangEula()
    }
}
