plugins {
    id("quark.published")
}

description = "Update checker for Modrinth, Hangar, GitHub Releases, SpigotMC and custom JSON endpoints"

dependencies {
    api(project(":quark-common"))
}
