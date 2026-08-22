plugins {
    id("quark.platform")
}

dependencies {
    api(project(":quark-core"))

    compileOnly("net.fabricmc:fabric-loader:0.17.3")
    compileOnly("org.slf4j:slf4j-api:2.0.20")
}
