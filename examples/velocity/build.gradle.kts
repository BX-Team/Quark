plugins {
    id("java")
    id("com.gradleup.shadow") version "9.1.0"
    id("xyz.jpenilla.run-velocity") version "2.3.1"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")

    // implementation("org.bxteam.quark:velocity:1.x.x") // <-- uncomment in your project and set the version
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

    runVelocity {
        velocityVersion("3.4.0-SNAPSHOT")
    }
}
