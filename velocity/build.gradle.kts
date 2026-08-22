plugins {
    id("quark.platform")
}

dependencies {
    api(project(":quark-core"))

    compileOnly("com.velocitypowered:velocity-api:3.1.1")
}
