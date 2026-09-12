package org.bxteam.quark.gradle

/**
 * Optional Quark modules that `quark { modules(...) }` adds to the project.
 * The platform adapter (and with it `quark-common` and `quark-platform-api`) comes from `quark.platform`.
 *
 * @property artifactId the Maven artifact of the module
 */
enum class QuarkModule(val artifactId: String) {
    /** Runtime dependency manager: `LibraryManager`, the dependency manifest, relocation. */
    DEPENDENCY("quark-dependency"),

    /** SLF4J and Log4j adapters for `QuarkLogger` and the debug switch. */
    LOGGER("quark-logger"),

    /** Update checker for Modrinth, Hangar, GitHub, SpigotMC and custom endpoints. */
    UPDATE("quark-update");

    companion object {
        /**
         * Parses a module name, ignoring case (`"dependency"`, `"UPDATE"`).
         */
        fun of(name: String): QuarkModule = values().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown Quark module '$name', expected one of ${values().joinToString { it.name.lowercase() }}")
    }
}
