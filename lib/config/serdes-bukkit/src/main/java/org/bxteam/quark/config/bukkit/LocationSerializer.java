package org.bxteam.quark.config.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bxteam.quark.config.ConfigNode;
import org.bxteam.quark.config.serdes.SerdesContext;
import org.bxteam.quark.config.serdes.SerializationException;
import org.bxteam.quark.config.serdes.TypeSerializer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;

/**
 * {@link Location}s as {@code world, x, y, z, yaw, pitch}. A location without a world has no {@code world} key.
 */
final class LocationSerializer implements TypeSerializer<Location> {
    @Override
    public Location deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
        if (!node.isMap()) {
            throw new SerializationException("expected a section with world, x, y and z");
        }
        World world = null;
        String worldName = node.node("world").getString();
        if (worldName != null) {
            world = Bukkit.getWorld(worldName);
            if (world == null) {
                throw new SerializationException("world '" + worldName + "' is not loaded. "
                        + "Load configurations with locations after the worlds, e.g. in onEnable");
            }
        }
        return new Location(world,
                coordinate(node, "x", context), coordinate(node, "y", context), coordinate(node, "z", context),
                (float) optional(node, "yaw", context), (float) optional(node, "pitch", context));
    }

    private static double coordinate(ConfigNode node, String key, SerdesContext context) {
        ConfigNode child = node.node(key);
        if (child.isNull()) {
            throw new SerializationException("missing " + key);
        }
        return context.deserialize(child, double.class);
    }

    private static double optional(ConfigNode node, String key, SerdesContext context) {
        ConfigNode child = node.node(key);
        return child.isNull() ? 0 : context.deserialize(child, double.class);
    }

    @Override
    public void serialize(@NotNull Type type, @NotNull Location value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
        World world = value.getWorld();
        if (world != null) {
            node.node("world").set(world.getName());
        }
        node.node("x").set(value.getX());
        node.node("y").set(value.getY());
        node.node("z").set(value.getZ());
        node.node("yaw").set(value.getYaw());
        node.node("pitch").set(value.getPitch());
    }
}
