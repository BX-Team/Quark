plugins {
    id("quark.published")
}

description = "Runtime Maven dependency manager: resolution, relocation and class loading"

dependencies {
    api(project(":quark-common"))
}
