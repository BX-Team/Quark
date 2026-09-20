package org.bxteam.quark.gradle

/**
 * Optional Quark modules that `quark { modules(...) }` adds to the project.
 * The platform adapter (and with it `quark-common` and `quark-platform-api`) comes from `quark.platform`.
 *
 * @property artifactIds the Maven artifacts of the module
 */
enum class QuarkModule(vararg val artifactIds: String) {
    /** Runtime dependency manager: `LibraryManager`, the dependency manifest, relocation. */
    DEPENDENCY("quark-dependency"),

    /** SLF4J and Log4j adapters for `QuarkLogger` and the debug switch. */
    LOGGER("quark-logger"),

    /** Update checker for Modrinth, Hangar, GitHub, SpigotMC and custom endpoints. */
    UPDATE("quark-update"),

    /**
     * Configuration objects with the YAML format. With a Bukkit-based `quark.platform` the Bukkit serializers
     * (`quark-config-serdes-bukkit`) are added too. snakeyaml-engine is downloaded at runtime when [DEPENDENCY]
     * is added as well, otherwise it must be shaded into the plugin.
     */
    CONFIG("quark-config", "quark-config-yaml"),

    /** `@NotNull`, `@Min`, `@Max`, `@Pattern` and `@Check` validation for configurations. */
    CONFIG_VALIDATOR("quark-config-validator");

    companion object {
        /**
         * Parses a module name, ignoring case and accepting hyphens (`"dependency"`, `"UPDATE"`, `"config-validator"`).
         */
        fun of(name: String): QuarkModule = values().firstOrNull { it.name.equals(name.trim().replace('-', '_'), ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown Quark module '$name', expected one of ${values().joinToString { it.name.lowercase().replace('_', '-') }}")
    }
}
