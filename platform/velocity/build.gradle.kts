plugins {
    id("quark.platform")
}

description = "Quark adapter for Velocity"

dependencies {
    api(project(":quark-logger"))
    compileOnly("com.velocitypowered:velocity-api:3.1.1")
}
