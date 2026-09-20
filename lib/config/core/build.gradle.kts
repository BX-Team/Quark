plugins {
    id("quark.published")
}

description = "Annotation-driven configuration objects: node tree, serializers, migrations and validation hooks"

dependencies {
    api(project(":quark-common"))

    // optional: downloads the format backend (snakeyaml-engine) at runtime when the plugin ships quark-dependency
    compileOnly(project(":quark-dependency"))
}
