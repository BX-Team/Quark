plugins {
    `quark-deps`
    `quark-java`
    `quark-publish`
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    api(project(":quark-core"))

    compileOnly("com.velocitypowered:velocity-api:3.1.1")
}
