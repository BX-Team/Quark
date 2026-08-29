plugins {
    id("quark.published")
}

dependencies {
    api(project(":quark-platform-api"))
    compileOnly(project(":quark-dependency"))
    testImplementation(project(":quark-dependency"))
}
