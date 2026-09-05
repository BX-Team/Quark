plugins {
    id("quark.published")
}

description = "QuarkLogger adapters for SLF4J and Log4j 2 and a global debug switch"

dependencies {
    api(project(":quark-common"))

    compileOnly("org.slf4j:slf4j-api:2.0.20")
    compileOnly("org.apache.logging.log4j:log4j-api:2.24.3")

    testImplementation("org.slf4j:slf4j-api:2.0.20")
    testImplementation("org.apache.logging.log4j:log4j-api:2.24.3")
}
