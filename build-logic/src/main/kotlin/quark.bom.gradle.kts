plugins {
    `java-platform`
    `maven-publish`
}

publishing {
    quarkRepository(project)

    publications {
        create<MavenPublication>("maven") {
            artifactId = project.name
            from(components["javaPlatform"])
            quarkPom(project)
        }
    }
}
