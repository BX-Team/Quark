rootProject.name = "Quark"

setOf(
    "bukkit",
    "bungee",
    "core",
    "paper",
    "velocity"
).forEach {
    subProject(it)
}

fun subProject(name: String) {
    include(":quark-$name")
    project(":quark-$name").projectDir = file(name)
}

// Examples
include(":examples:bukkit")
include(":examples:bungee")
include(":examples:paper")
include(":examples:velocity")
