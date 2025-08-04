repositories {
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
}

dependencies {
    api(project(":quark-core"))

    compileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
    testCompileOnly("org.spigotmc:spigot-api:1.19.4-R0.1-SNAPSHOT")
}

val buildTestPlugin by tasks.registering(Jar::class) {
    archiveBaseName.set("QuarkBukkitTest")

    destinationDirectory.set(file("$buildDir/test-plugin"))

    from(sourceSets.main.get().output)
    from(sourceSets.test.get().output)

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    from(project(":quark-core").tasks.jar.get().outputs.files.map { zipTree(it) })

    from("src/test/resources")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    dependsOn(tasks.compileJava, tasks.compileTestJava, project(":quark-core").tasks.jar)
}

buildTestPlugin {
    group = "build"
    description = "Builds a test plugin JAR with embedded dependencies for server testing"
}
