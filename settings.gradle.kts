rootProject.name = "Quark"

includeBuild("gradle-plugin")

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
