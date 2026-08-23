package org.bxteam.quark.platform;

/**
 * Server platforms supported by Quark adapters.
 */
public enum PlatformType {
    /** Bukkit and Spigot servers. */
    BUKKIT,
    /** Paper servers and forks, including Folia. */
    PAPER,
    /** BungeeCord and Waterfall proxies. */
    BUNGEE,
    /** Velocity proxies. */
    VELOCITY;

    /**
     * @return true for proxy platforms, which have no worlds and no main thread
     */
    public boolean isProxy() {
        return this == BUNGEE || this == VELOCITY;
    }
}
