plugins {
    id("java-library")
}

allprojects {
    group = project.group
    version = project.version

    repositories {
        mavenCentral()
    }
}
