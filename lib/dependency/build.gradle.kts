plugins {
    id("quark.published")
}

description = "Runtime Maven dependency manager: resolution, relocation and class loading"

val asmVersion = "9.11"

dependencies {
    api(project(":quark-common"))

    // the relocator (relocation.asm) is compiled against ASM, which is downloaded at runtime; versions match RelocationHandler
    compileOnly("org.ow2.asm:asm:$asmVersion")
    compileOnly("org.ow2.asm:asm-commons:$asmVersion")

    // also served by the fake Maven repository in RelocationTest
    testImplementation("org.ow2.asm:asm:$asmVersion")
    testImplementation("org.ow2.asm:asm-commons:$asmVersion")
}
