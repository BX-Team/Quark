val sizeBudget = configurations.create("sizeBudget") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

dependencies {
    sizeBudget(project(":quark-paper"))
    sizeBudget(project(":quark-dependency"))
}

abstract class CheckArtifactSize : DefaultTask() {
    @get:InputFiles
    abstract val artifacts: ConfigurableFileCollection

    @get:Input
    abstract val maxKilobytes: Property<Long>

    @TaskAction
    fun check() {
        val jars = artifacts.files.sortedBy { it.name }
        jars.forEach { logger.lifecycle("  %-48s %7.1f KB".format(it.name, it.length() / 1024.0)) }

        val totalKb = jars.sumOf { it.length() } / 1024.0
        val limit = maxKilobytes.get()
        logger.lifecycle("  total: %.1f KB (budget %d KB)".format(totalKb, limit))

        if (totalKb > limit) {
            throw GradleException("Artifact size budget exceeded: %.1f KB > %d KB".format(totalKb, limit))
        }
    }
}

tasks.register<CheckArtifactSize>("checkArtifactSize") {
    group = "verification"
    description = "Fails when the minimal Quark setup exceeds the size budget"
    artifacts.from(sizeBudget)
    maxKilobytes = 200L
}
