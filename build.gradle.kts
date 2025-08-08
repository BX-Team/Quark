plugins {
    `java-library`
    `maven-publish`
}

allprojects {
    group = project.group
    version = project.version

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    dependencies {
        compileOnly("org.jetbrains:annotations:26.0.2")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    tasks.withType<Javadoc> {
        (options as StandardJavadocDocletOptions).apply {
            encoding = Charsets.UTF_8.name()
            use()
            tags("apiNote:a:API Note:")
        }
    }

    publishing {
        repositories {
            maven {
                name = "quark"
                url = uri("https://repo.bxteam.org/releases/")

                if (version.toString().endsWith("-SNAPSHOT")) {
                    url = uri("https://repo.bxteam.org/snapshots/")
                }

                credentials.username = System.getenv("REPO_USERNAME")
                credentials.password = System.getenv("REPO_PASSWORD")
            }
        }
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = project.name.removePrefix("quark-")
                version = project.version.toString()
                from(components["java"])
            }
        }
    }
}
