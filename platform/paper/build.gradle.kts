plugins {
    id("quark.platform")
}

description = "Quark adapter for Paper and Folia"

dependencies {
    api(project(":quark-bukkit"))
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
}
