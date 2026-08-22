plugins {
    id("quark.base")
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    repositories {
        maven {
            name = "quark"
            val snapshot = version.toString().endsWith("-SNAPSHOT")
            url = uri(if (snapshot) "https://repo.bxteam.org/snapshots/" else "https://repo.bxteam.org/releases/")

            credentials {
                username = System.getenv("REPO_USERNAME")
                password = System.getenv("REPO_PASSWORD")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            artifactId = project.name
            from(components["java"])

            pom {
                name = project.name
                description = project.description
                url = "https://github.com/BX-Team/Quark"

                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }

                scm {
                    url = "https://github.com/BX-Team/Quark"
                    connection = "scm:git:https://github.com/BX-Team/Quark.git"
                }
            }
        }
    }
}
