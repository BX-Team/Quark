plugins {
    id("quark.published")
}

description = "YAML format for Quark configurations, on snakeyaml-engine loaded from the class path or at runtime"

val snakeyamlEngine = "org.snakeyaml:snakeyaml-engine:3.2"

dependencies {
    api(project(":quark-config"))

    // not shipped with the module: BackendProvider finds it on the class path or downloads it with quark-dependency
    compileOnly(snakeyamlEngine)
    testImplementation(snakeyamlEngine)
}

// the version BackendProvider downloads, kept in sync with the compileOnly dependency above
val generateBackendVersion = tasks.register("generateBackendVersion") {
    val outputDir = layout.buildDirectory.dir("generated/backend-version")
    val coordinates = snakeyamlEngine
    inputs.property("coordinates", coordinates)
    outputs.dir(outputDir)

    doLast {
        val file = outputDir.get().file("org/bxteam/quark/config/yaml/SnakeYamlVersion.java").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package org.bxteam.quark.config.yaml;
            |
            |final class SnakeYamlVersion {
            |    static final String COORDINATES = "$coordinates";
            |
            |    private SnakeYamlVersion() {
            |    }
            |}
            |""".trimMargin()
        )
    }
}

sourceSets.main {
    java.srcDir(generateBackendVersion)
}
