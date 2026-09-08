plugins {
    id("quark.published")
}

dependencies {
    api(project(":quark-platform-api"))
    compileOnly(project(":quark-dependency"))
    compileOnly(project(":quark-update"))
    testImplementation(project(":quark-dependency"))
    testImplementation(project(":quark-update"))
}
