pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "quark"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/groups/public/")
        maven("https://repo.spongepowered.org/maven/")
        maven("https://maven.fabricmc.net")
    }
}

includeBuild("gradle-plugin")

module("quark-common", "common")
module("quark-platform-api", "platform/api")
module("quark-dependency", "lib/dependency")

setOf(
    "bukkit",
    "paper",
    "velocity"
).forEach {
    module("quark-$it", "platform/$it")
}

fun module(name: String, path: String) {
    include(":$name")
    project(":$name").projectDir = file(path)
}
