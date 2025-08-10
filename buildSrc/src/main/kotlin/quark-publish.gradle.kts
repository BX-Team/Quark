plugins {
    id("java-library")
    id("maven-publish")
}

group = project.group
version = project.version

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).apply {
        encoding = Charsets.UTF_8.name()
        use()
        tags("apiNote:a:API Note:")
    }
}

publishing {
    java {
        withSourcesJar()
        withJavadocJar()
    }

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
