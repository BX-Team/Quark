import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

/**
 * Repository and POM metadata shared by every published Quark artifact.
 */
fun PublishingExtension.quarkRepository(project: Project) {
    repositories {
        maven {
            name = "quark"
            val snapshot = project.version.toString().endsWith("-SNAPSHOT")
            url = project.uri(if (snapshot) "https://repo.bxteam.org/snapshots/" else "https://repo.bxteam.org/releases/")

            credentials {
                username = System.getenv("REPO_USERNAME")
                password = System.getenv("REPO_PASSWORD")
            }
        }
        // build-local repository, used by the Gradle plugin's functional tests
        maven {
            name = "buildLocal"
            url = project.rootProject.layout.buildDirectory.dir("local-repo").get().asFile.toURI()
        }
    }
}

fun MavenPublication.quarkPom(project: Project) {
    pom {
        name.set(project.name)
        description.set(project.provider { project.description })
        url.set("https://github.com/BX-Team/Quark")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        scm {
            url.set("https://github.com/BX-Team/Quark")
            connection.set("scm:git:https://github.com/BX-Team/Quark.git")
        }
    }
}
