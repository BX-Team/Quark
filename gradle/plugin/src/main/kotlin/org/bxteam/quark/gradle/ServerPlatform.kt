package org.bxteam.quark.gradle

/**
 * The platform a plugin targets.
 *
 * Selects the platform adapter added to the project ([adapterArtifactId]) and the default [ServerType] of
 * dev servers. Named `ServerPlatform` to avoid confusion with `org.bxteam.quark.platform.Platform` from
 * `quark-platform-api`.
 *
 * @property adapterArtifactId the Quark adapter for this platform
 * @property defaultServerType the server dev servers run unless they set a type
 */
enum class ServerPlatform(val adapterArtifactId: String, val defaultServerType: ServerType) {
    PAPER("quark-paper", ServerType.PAPER),
    FOLIA("quark-paper", ServerType.FOLIA),
    BUKKIT("quark-bukkit", ServerType.SPIGOT),
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
