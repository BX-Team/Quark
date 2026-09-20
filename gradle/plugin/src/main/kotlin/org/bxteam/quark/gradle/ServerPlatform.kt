package org.bxteam.quark.gradle

/**
 * The platform a plugin targets.
 *
 * Selects the platform adapter added to the project ([adapterArtifactId]), the default [ServerType] of
 * dev servers and the configuration serializers ([serdesArtifactId]). Named `ServerPlatform` to avoid confusion with `org.bxteam.quark.platform.Platform` from
 * `quark-platform-api`.
 *
 * @property adapterArtifactId the Quark adapter for this platform
 * @property defaultServerType the server dev servers run unless they set a type
 * @property serdesArtifactId the configuration serializers for this platform, added with [QuarkModule.CONFIG]
 */
enum class ServerPlatform(val adapterArtifactId: String, val defaultServerType: ServerType, val serdesArtifactId: String? = null) {
    PAPER("quark-paper", ServerType.PAPER, "quark-config-serdes-bukkit"),
    FOLIA("quark-paper", ServerType.FOLIA, "quark-config-serdes-bukkit"),
    BUKKIT("quark-bukkit", ServerType.SPIGOT, "quark-config-serdes-bukkit"),
    BUNGEE("quark-bungee", ServerType.BUNGEECORD),
    VELOCITY("quark-velocity", ServerType.VELOCITY);

    companion object {
        /**
         * Parses a platform name, ignoring case (`"paper"`, `"VELOCITY"`).
         */
        fun of(name: String): ServerPlatform = values().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            ?: throw IllegalArgumentException("Unknown platform '$name', expected one of ${values().joinToString { it.name.lowercase() }}")
    }
}
