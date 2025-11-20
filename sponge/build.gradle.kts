plugins {
    `quark-deps`
    `quark-java`
    `quark-publish`
}

repositories {
    maven("https://repo.spongepowered.org/maven/")
}

dependencies {
    api(project(":quark-core"))

    compileOnly("org.spongepowered:spongeapi:8.1.0")
}
