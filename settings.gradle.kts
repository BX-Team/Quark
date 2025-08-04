rootProject.name = "Quark"

setOf(
    "bukkit",
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
