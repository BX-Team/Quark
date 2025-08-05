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
