import org.bxteam.runserver.ServerType

plugins {
    id("java")
    id("com.gradleup.shadow") version "8.3.9"
    id("org.bxteam.runserver") version "1.2.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    // implementation("org.bxteam.quark:velocity:1.0.0") // <-- uncomment in your project
    implementation(project(":quark-velocity")) // don't use this line in your build file
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
        archiveBaseName.set("VelocityExamplePlugin")
        archiveClassifier.set("")
        minimize()
    }

    runServer {
        serverType(ServerType.VELOCITY)
        serverVersion("3.4.0-SNAPSHOT")
    }
}
