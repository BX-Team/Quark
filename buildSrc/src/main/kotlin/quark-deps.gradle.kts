plugins {
    id("java-library")
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.1.0")

    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
}
