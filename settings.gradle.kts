pluginManagement {
    includeBuild("gradle-plugin")
}

rootProject.name = "Quark"

setOf(
    "bukkit",
    "bungee",
    "core",
    "fabric",
    "paper",
    "sponge",
    "velocity"
).forEach {
    subProject(it)
}

fun subProject(name: String) {
    include(":quark-$name")
    project(":quark-$name").projectDir = file(name)
}

setOf(
    "bukkit",
    "bungee",
    "gradle",
    "paper",
    "velocity"
).forEach {
    exampleProject(it)
}

fun exampleProject(name: String) {
    include(":examples:$name")
    project(":examples:$name").projectDir = file("examples/$name")
}
