package org.bxteam.quark.config.bukkit;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.util.Vector;
import org.bxteam.quark.config.ConfigNode;
import org.bxteam.quark.config.serdes.SerdesContext;
import org.bxteam.quark.config.serdes.SerdesPack;
import org.bxteam.quark.config.serdes.SerdesRegistry;
import org.bxteam.quark.config.serdes.SerializationException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BukkitSerdesTest {
    private final SerdesContext context = new SerdesContext(SerdesRegistry.create().register(new BukkitSerdes()));

    /**
     * A serializable type with a nested serializable value, like ItemStack with its ItemMeta.
     */
    public static final class Waypoint implements ConfigurationSerializable {
        final String name;
        final Vector position;

        Waypoint(String name, Vector position) {
            this.name = name;
            this.position = position;
        }

        public static Waypoint deserialize(Map<String, Object> map) {
            return new Waypoint((String) map.get("name"), (Vector) map.get("position"));
        }

        @Override
        public @NotNull Map<String, Object> serialize() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("position", position);
            return map;
        }
    }

    static {
        ConfigurationSerialization.registerClass(Waypoint.class, "Waypoint");
    }

    private Object roundTrip(Object value, Type type, ConfigNode written) {
        context.serialize(value, type, written);
        return context.deserialize(ConfigNode.of(written.raw()), type);
    }

    @Test
    void configurationSerializablesKeepNestedAliases() {
        ConfigNode written = ConfigNode.root();
        Waypoint back = (Waypoint) roundTrip(new Waypoint("spawn", new Vector(1, 2, 3)), Waypoint.class, written);

        assertEquals(Map.of("name", "spawn", "position", Map.of("==", "Vector", "x", 1.0, "y", 2.0, "z", 3.0)), written.raw());
        assertEquals("spawn", back.name);
        assertEquals(new Vector(1, 2, 3), back.position);
    }

    @Test
    void abstractDeclaredTypesStoreTheAlias() {
        ConfigNode written = ConfigNode.root();
        Object back = roundTrip(new Vector(4, 5, 6), ConfigurationSerializable.class, written);

        assertEquals("Vector", written.node("==").getString());
        assertEquals(new Vector(4, 5, 6), back);
    }

    @Test
    void locationsWithoutWorld() {
        ConfigNode written = ConfigNode.root();
        Location back = (Location) roundTrip(new Location(null, 1.5, 64, -3, 90, 10), Location.class, written);

        assertEquals(Map.of("x", 1.5, "y", 64.0, "z", -3.0, "yaw", 90.0f, "pitch", 10.0f), written.raw());
        assertEquals(new Location(null, 1.5, 64, -3, 90, 10), back);
        SerializationException missing = assertThrows(SerializationException.class,
                () -> context.deserialize(ConfigNode.of(Map.of("x", 1, "y", 2)), Location.class));
        assertTrue(missing.getMessage().contains("missing z"), missing.getMessage());
    }

    @Test
    void soundsAsKeysAndEnumNames() {
        ConfigNode written = ConfigNode.root();
        context.serialize(Sound.ENTITY_PLAYER_LEVELUP, Sound.class, written);

        assertEquals("entity.player.levelup", written.getString());
        assertEquals(Sound.ENTITY_PLAYER_LEVELUP, context.deserialize(ConfigNode.of("ENTITY_PLAYER_LEVELUP"), Sound.class));
    }

    @Test
    void componentsAsMiniMessage() {
        ConfigNode written = ConfigNode.root();
        Component back = (Component) roundTrip(Component.text("Hello", NamedTextColor.RED), Component.class, written);

        assertEquals("<red>Hello", written.getString());
        assertEquals(Component.text("Hello", NamedTextColor.RED), back);
        assertEquals(List.of(Component.text("a")), context.deserialize(ConfigNode.of(List.of("a")),
                new org.bxteam.quark.config.serdes.TypeToken<List<Component>>() {}));
    }

    @Test
    void registeredWithServiceLoader() {
        List<SerdesPack> packs = ServiceLoader.load(SerdesPack.class).stream().map(ServiceLoader.Provider::get).toList();

        assertEquals(1, packs.size());
        assertInstanceOf(BukkitSerdes.class, packs.get(0));
    }
}
