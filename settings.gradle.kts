rootProject.name = "Quark"

setOf(
    "bukkit",
    "core",
    "example"
).forEach {
    subProject(it)
}

fun subProject(name: String) {
    include(":quark-$name")
    project(":quark-$name").projectDir = file(name)
}
