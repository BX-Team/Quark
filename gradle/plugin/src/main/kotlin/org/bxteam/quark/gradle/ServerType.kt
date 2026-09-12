package org.bxteam.quark.gradle

/**
 * Server software a dev server can run. Server JARs are downloaded from the [MCJars](https://mcjars.app) API.
 *
 * @property apiName the type name in the MCJars API
 * @property loader the Modrinth loader of plugins built for this server; forks use the loader of the server
 *                  they are based on (Pufferfish, DivineMC, Leaf, Leaves: `paper`, Canvas: `folia`)
 * @property proxy whether this is a proxy
 */
enum class ServerType(val apiName: String, val loader: String, val proxy: Boolean) {
    SPIGOT("SPIGOT", "spigot", false),
    PAPER("PAPER", "paper", false),
    FOLIA("FOLIA", "folia", false),
    PUFFERFISH("PUFFERFISH", "paper", false),
    PURPUR("PURPUR", "purpur", false),
    CANVAS("CANVAS", "folia", false),
    DIVINEMC("DIVINEMC", "paper", false),
    LEAF("LEAF", "paper", false),
    LEAVES("LEAVES", "paper", false),
    BUNGEECORD("BUNGEECORD", "bungeecord", true),
    WATERFALL("WATERFALL", "waterfall", true),
    VELOCITY("VELOCITY", "velocity", true),
    VELOCITY_CTD("VELOCITY_CTD", "velocity", true);

    /** Whether Paper plugins run on this server. */
    val paperCompatible: Boolean
        get() = this in setOf(PAPER, FOLIA, PUFFERFISH, PURPUR, CANVAS, DIVINEMC, LEAF, LEAVES)

    /** Whether a plugin published for [loader] runs on this server. */
    fun accepts(loader: String): Boolean = when {
        loader.equals(this.loader, ignoreCase = true) -> true
        paperCompatible && (loader.equals("paper", ignoreCase = true) || loader.equals("bukkit", ignoreCase = true) || loader.equals("spigot", ignoreCase = true)) -> true
        this == SPIGOT && loader.equals("bukkit", ignoreCase = true) -> true
        this == WATERFALL && loader.equals("bungeecord", ignoreCase = true) -> true
        else -> false
    }
}

/**
 * Unit of the dev server heap size.
 */
enum class RamUnit(val flag: String) {
    MB("M"),
    GB("G")
}
