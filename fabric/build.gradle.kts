plugins {
    `quark-deps`
    `quark-java`
    `quark-publish`
}

repositories {
    maven("https://maven.fabricmc.net")
}

dependencies {
    api(project(":quark-core"))

    compileOnly("net.fabricmc:fabric-loader:0.17.2")
    compileOnly("org.slf4j:slf4j-api:2.0.17")
}
