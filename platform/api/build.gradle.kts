plugins {
    id("quark.published")
}

description = "Contract between Quark modules and the server platform"

dependencies {
    api(project(":quark-common"))
}
