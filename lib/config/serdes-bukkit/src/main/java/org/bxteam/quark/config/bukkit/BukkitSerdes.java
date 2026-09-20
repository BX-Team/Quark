package org.bxteam.quark.config.bukkit;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;
import org.bxteam.quark.config.serdes.SerdesPack;
import org.bxteam.quark.config.serdes.SerdesRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Serializers for Bukkit types:
 *
 * <ul>
 *     <li>{@link ItemStack} and every other {@link ConfigurationSerializable} (ItemMeta, Color, Vector,
 *     PotionEffect, ...), in the format Bukkit's own YAML configuration uses;</li>
 *     <li>{@link Location} as {@code world, x, y, z, yaw, pitch}; the world must be loaded when the configuration
 *     is loaded;</li>
 *     <li>{@link Sound} as its key, for example {@code entity.player.levelup}; enum names are accepted too;</li>
 *     <li>Adventure {@code Component} as a MiniMessage string, when MiniMessage is on the class path (Paper).</li>
 * </ul>
 *
 * <p>Registered with {@link java.util.ServiceLoader}, so configurations use it as soon as this module is on the
 * class path. The Quark Gradle plugin adds it for {@code platform = PAPER, FOLIA or BUKKIT} with the config module.</p>
 */
public final class BukkitSerdes implements SerdesPack {
    private static final String MINI_MESSAGE = "net.kyori.adventure.text.minimessage.MiniMessage";

    /**
     * Creates the pack. Used by {@link java.util.ServiceLoader}.
     */
    public BukkitSerdes() {
    }

    @Override
    public void register(@NotNull SerdesRegistry registry) {
        registry.registerHierarchy(ConfigurationSerializable.class, new ConfigurationSerializableSerializer());
        registry.registerHierarchy(Location.class, new LocationSerializer());
        registry.registerHierarchy(Sound.class, new SoundSerializer());
        if (isPresent(MINI_MESSAGE)) {
            ComponentSerializer.register(registry);
        }
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, BukkitSerdes.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
