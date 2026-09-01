plugins {
    id("quark.published")
}

description = "Runtime Maven dependency manager: resolution, relocation and class loading"

dependencies {
    api(project(":quark-common"))

    // served by the fake Maven repository in RelocationTest, versions match RelocationHandler
    testImplementation("org.ow2.asm:asm:9.7")
    testImplementation("org.ow2.asm:asm-commons:9.7")
    testImplementation("me.lucko:jar-relocator:1.7")
}
