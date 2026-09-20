package org.bxteam.quark.config.serdes;

import org.bxteam.quark.config.ConfigNode;
import org.bxteam.quark.config.annotation.Comment;
import org.bxteam.quark.config.annotation.NameStrategy;
import org.bxteam.quark.config.annotation.NameStyle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerdesTest {
    private final SerdesContext context = new SerdesContext(SerdesRegistry.create());

    enum Color { RED, DARK_BLUE }

    @NameStrategy(NameStyle.SNAKE_CASE)
    static class Settings {
        @Comment("the name")
        public String displayName = "default";
        public Inner inner = new Inner();
        public Map<Color, List<Integer>> byColor = new EnumMap<>(Color.class);
    }

    static class Inner {
        public int someValue = 1;
    }

    record Point(int x, int y, String label) {
    }

    private Object roundTrip(Object value, java.lang.reflect.Type type) {
        ConfigNode node = ConfigNode.root();
        context.serialize(value, type, node);
        return context.deserialize(ConfigNode.of(node.raw()), type);
    }

    @Test
    void scalars() {
        assertEquals(5, context.deserialize(ConfigNode.of("5"), int.class));
        assertEquals(5L, context.deserialize(ConfigNode.of(5), Long.class));
        assertEquals(1.5, context.deserialize(ConfigNode.of(1.5), double.class));
        assertEquals(Double.POSITIVE_INFINITY, context.deserialize(ConfigNode.of(".inf"), Double.class));
        assertEquals(new BigDecimal("0.10"), context.deserialize(ConfigNode.of("0.10"), BigDecimal.class));
        assertEquals(true, context.deserialize(ConfigNode.of("yes"), boolean.class));
        assertEquals('x', context.deserialize(ConfigNode.of("x"), char.class));
        assertEquals("12", context.deserialize(ConfigNode.of(12), String.class));
        assertEquals(Color.DARK_BLUE, context.deserialize(ConfigNode.of("dark-blue"), Color.class));
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid, roundTrip(uuid, UUID.class));
        Instant instant = Instant.parse("2025-01-31T12:00:00Z");
        assertEquals(instant, roundTrip(instant, Instant.class));
    }

    @Test
    void scalarErrorsAreExplained() {
        SerializationException tooBig = assertThrows(SerializationException.class,
                () -> context.deserialize(ConfigNode.root().node("a").set(3_000_000_000L), int.class));
        assertTrue(tooBig.getMessage().startsWith("a: '3000000000' does not fit Integer"), tooBig.getMessage());

        SerializationException fraction = assertThrows(SerializationException.class, () -> context.deserialize(ConfigNode.of(1.5), int.class));
        assertTrue(fraction.getMessage().contains("expected a whole number"), fraction.getMessage());

        SerializationException color = assertThrows(SerializationException.class, () -> context.deserialize(ConfigNode.of("green"), Color.class));
        assertTrue(color.getMessage().contains("expected one of RED, DARK_BLUE"), color.getMessage());

        assertThrows(SerializationException.class, () -> context.deserialize(ConfigNode.of(List.of()), String.class));
    }

    @Test
    void durations() {
        assertEquals(Duration.ofSeconds(90), context.deserialize(ConfigNode.of("1m30s"), Duration.class));
        assertEquals(Duration.ofMillis(1500), context.deserialize(ConfigNode.of("1s 500ms"), Duration.class));
        assertEquals(Duration.ofDays(2).plusHours(3), context.deserialize(ConfigNode.of("2d3h"), Duration.class));
        assertEquals(Duration.ofSeconds(30), context.deserialize(ConfigNode.of(30), Duration.class));
        assertEquals(Duration.ofMinutes(5), context.deserialize(ConfigNode.of("PT5M"), Duration.class));
        assertEquals(Duration.ofMinutes(-5), context.deserialize(ConfigNode.of("-5m"), Duration.class));
        assertThrows(SerializationException.class, () -> context.deserialize(ConfigNode.of("5 minutes"), Duration.class));

        ConfigNode node = ConfigNode.root();
        context.serialize(Duration.ofHours(25).plusMillis(10), Duration.class, node);
        assertEquals("1d1h10ms", node.getString());
    }

    @Test
    void collectionsArraysAndMaps() {
        assertEquals(List.of(1, 2), context.deserialize(ConfigNode.of(List.of("1", 2)), new TypeToken<List<Integer>>() {}));
        assertEquals(List.of("solo"), context.deserialize(ConfigNode.of("solo"), new TypeToken<List<String>>() {}));
        Set<String> set = context.deserialize(ConfigNode.of(List.of("b", "a")), new TypeToken<SortedSet<String>>() {});
        assertInstanceOf(TreeSet.class, set);
        assertArrayEquals(new int[]{1, 2}, (int[]) context.deserialize(ConfigNode.of(List.of(1, 2)), int[].class));
        Map<UUID, Integer> map = context.deserialize(ConfigNode.of(Map.of("00000000-0000-0000-0000-000000000001", 5)),
                new TypeToken<Map<UUID, Integer>>() {});
        assertEquals(Map.of(new UUID(0, 1), 5), map);
        assertEquals(Optional.of(3), context.deserialize(ConfigNode.of(3), new TypeToken<Optional<Integer>>() {}));
        assertEquals(Optional.empty(), context.deserialize(ConfigNode.root().node("missing"), new TypeToken<Optional<Integer>>() {}));
        assertEquals(Map.of("a", List.of(1)), context.deserialize(ConfigNode.of(Map.of("a", List.of(1))), Object.class));
    }

    @Test
    void objectsAndRecords() {
        Settings settings = new Settings();
        settings.displayName = "custom";
        settings.inner.someValue = 4;
        settings.byColor.put(Color.RED, List.of(1, 2));

        ConfigNode node = ConfigNode.root();
        context.serialize(settings, Settings.class, node);

        assertEquals(Map.of("display_name", "custom", "inner", Map.of("some_value", 4), "by_color", Map.of("RED", List.of(1, 2))), node.raw());
        assertEquals(List.of("the name"), node.node("display_name").comment());

        Settings back = context.deserialize(ConfigNode.of(node.raw()), Settings.class);
        assertEquals("custom", back.displayName);
        assertEquals(4, back.inner.someValue, "nested classes inherit the name strategy");
        assertEquals(List.of(1, 2), back.byColor.get(Color.RED));

        Point point = context.deserialize(ConfigNode.of(Map.of("x", 1, "label", "spawn")), Point.class);
        assertEquals(new Point(1, 0, "spawn"), point);
    }

    @Test
    void nullsAndCustomSerializers() {
        assertNull(context.deserialize(ConfigNode.root().node("x"), String.class));
        ConfigNode node = ConfigNode.root().node("x");
        context.serialize(null, String.class, node);
        assertTrue(node.isNull());

        SerdesRegistry registry = SerdesRegistry.create()
                .register(Color.class, TypeSerializer.ofString(string -> string.equals("r") ? Color.RED : Color.DARK_BLUE,
                        color -> color == Color.RED ? "r" : "b"));
        SerdesContext custom = new SerdesContext(registry);
        assertEquals(Color.RED, custom.deserialize(ConfigNode.of("r"), Color.class));

        ConfigNode written = ConfigNode.root();
        custom.serialize(List.of(Color.DARK_BLUE), new TypeToken<List<Color>>() {}.type(), written);
        assertEquals(List.of("b"), written.raw());
    }

    @Test
    void unsupportedTypesNameTheType() {
        SerializationException error = assertThrows(SerializationException.class,
                () -> context.deserialize(ConfigNode.of("x"), Runnable.class));
        assertTrue(error.getMessage().contains("No serializer for java.lang.Runnable"), error.getMessage());
    }
}
