plugins {
    id("quark.bom")
}

description = "Bill of materials that aligns the versions of all Quark modules"

dependencies {
    constraints {
        api(project(":quark-common"))
        api(project(":quark-platform-api"))
        api(project(":quark-dependency"))
        api(project(":quark-logger"))
        api(project(":quark-bukkit"))
        api(project(":quark-paper"))
        api(project(":quark-velocity"))
    }
}
