plugins {
    id("quark.platform")
}

dependencies {
    api(project(":quark-core"))

    compileOnly("org.spongepowered:spongeapi:8.1.0")
}
