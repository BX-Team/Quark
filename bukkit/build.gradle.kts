plugins {
    id("quark.platform")
}

dependencies {
    api(project(":quark-dependency"))

    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
}
