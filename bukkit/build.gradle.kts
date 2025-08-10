plugins {
    `quark-deps`
    `quark-java`
    `quark-publish`
}

repositories {
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
}

dependencies {
    api(project(":quark-core"))

    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
}
