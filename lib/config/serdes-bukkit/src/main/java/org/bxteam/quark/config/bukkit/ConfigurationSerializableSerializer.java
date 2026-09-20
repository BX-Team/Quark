package org.bxteam.quark.config.bukkit;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bxteam.quark.config.ConfigNode;
import org.bxteam.quark.config.serdes.SerdesContext;
import org.bxteam.quark.config.serdes.SerializationException;
import org.bxteam.quark.config.serdes.TypeSerializer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ConfigurationSerializable}s as the maps of {@link ConfigurationSerializable#serialize()}. Nested
 * serializable values carry their class alias under {@code ==}, like in Bukkit's YAML configuration; the top
 * level only does when the declared type does not name a concrete class.
 */
final class ConfigurationSerializableSerializer implements TypeSerializer<ConfigurationSerializable> {
    @Override
    @SuppressWarnings("unchecked")
    public ConfigurationSerializable deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
        if (!node.isMap()) {
            throw new SerializationException("expected a section, found '" + node.raw() + "'");
        }
        Map<String, Object> map = reviveEntries((Map<?, ?>) node.raw());
        Class<?> raw = raw(type);

        ConfigurationSerializable result;
        try {
            if (map.containsKey(ConfigurationSerialization.SERIALIZED_TYPE_KEY) || !concrete(raw)) {
                result = ConfigurationSerialization.deserializeObject(map);
            } else {
                result = ConfigurationSerialization.deserializeObject(map, (Class<? extends ConfigurationSerializable>) raw);
            }
        } catch (RuntimeException e) {
            throw new SerializationException("cannot read " + raw.getSimpleName() + ": " + e, e);
        }
        if (result == null) {
            throw new SerializationException("cannot read " + raw.getSimpleName() + " from " + map
                    + (concrete(raw) ? "" : ", the section needs a '==' key naming its type"));
        }
        if (!raw.isInstance(result)) {
            throw new SerializationException("expected " + raw.getSimpleName() + ", found " + result.getClass().getSimpleName());
        }
        return result;
    }

    @Override
    public void serialize(@NotNull Type type, @NotNull ConfigurationSerializable value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (!concrete(raw(type))) {
            map.put(ConfigurationSerialization.SERIALIZED_TYPE_KEY, ConfigurationSerialization.getAlias(value.getClass()));
        }
        value.serialize().forEach((key, element) -> map.put(key, flatten(element)));
        node.set(map);
    }

    /**
     * Turns nested serializable objects into maps with their alias.
     */
    static Object flatten(Object value) {
        if (value instanceof ConfigurationSerializable serializable) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put(ConfigurationSerialization.SERIALIZED_TYPE_KEY, ConfigurationSerialization.getAlias(serializable.getClass()));
            serializable.serialize().forEach((key, element) -> map.put(key, flatten(element)));
            return map;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, element) -> result.put(String.valueOf(key), flatten(element)));
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(element -> result.add(flatten(element)));
            return result;
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value.getClass().isArray()) {
            return value;
        }
        if (value instanceof Enum<?> constant) {
            return constant.name();
        }
        return String.valueOf(value);
    }

    /**
     * Turns nested maps with an alias back into objects, inside out.
     */
    static Object revive(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> entries = reviveEntries(map);
            return entries.containsKey(ConfigurationSerialization.SERIALIZED_TYPE_KEY) ? deserializeNested(entries) : entries;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(element -> result.add(revive(element)));
            return result;
        }
        return value;
    }

    static Map<String, Object> reviveEntries(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, element) -> result.put(String.valueOf(key), revive(element)));
        return result;
    }

    private static Object deserializeNested(Map<String, Object> map) {
        ConfigurationSerializable result = ConfigurationSerialization.deserializeObject(map);
        if (result == null) {
            throw new SerializationException("cannot read the nested " + map.get(ConfigurationSerialization.SERIALIZED_TYPE_KEY) + " " + map);
        }
        return result;
    }

    private static Class<?> raw(Type type) {
        if (type instanceof Class<?> clazz) return clazz;
        if (type instanceof ParameterizedType parameterized) return (Class<?>) parameterized.getRawType();
        return ConfigurationSerializable.class;
    }

    private static boolean concrete(Class<?> type) {
        return !type.isInterface() && !Modifier.isAbstract(type.getModifiers());
    }
}
