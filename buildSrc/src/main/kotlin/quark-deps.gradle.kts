plugins {
    id("java-library")
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.2-1")

    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}
