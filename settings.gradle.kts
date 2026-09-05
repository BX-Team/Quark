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
    }
}

module("quark-common", "common")
module("quark-platform-api", "platform/api")
module("quark-dependency", "lib/dependency")
module("quark-logger", "lib/logger")
module("quark-bom", "bom")
module("quark-gradle-plugin", "gradle/plugin")

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
