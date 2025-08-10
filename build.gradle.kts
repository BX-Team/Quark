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
