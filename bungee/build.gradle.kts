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

    compileOnly("net.md-5:bungeecord-api:1.19-R0.1-SNAPSHOT")
    testCompileOnly("net.md-5:bungeecord-api:1.19-R0.1-SNAPSHOT")
}
