package org.bxteam.quark.config.serdes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import static java.util.Objects.requireNonNull;

/**
 * The serializers known to a configuration.
 *
 * <p>{@link #create()} starts with the standard serializers: primitives and their wrappers, {@link String},
 * {@code BigInteger}, {@code BigDecimal}, enums, {@code UUID}, {@code Duration}, {@code Instant}, {@code Optional},
 * collections, arrays, maps, {@link org.bxteam.quark.config.ConfigNode} and {@code Object} (plain values).
 * Any other class with a no-argument constructor, and any record, is bound field by field like the configuration
 * itself.</p>
 *
 * <p>Serializers registered later win over earlier ones, so registrations can replace standard serializers.</p>
 */
public final class SerdesRegistry {
    private final List<Entry> entries = new CopyOnWriteArrayList<>();
    private final Map<Type, Optional<TypeSerializer<?>>> cache = new ConcurrentHashMap<>();

    private SerdesRegistry() {
    }

    /**
     * @return a registry with the standard serializers
     */
    @NotNull
    public static SerdesRegistry create() {
        SerdesRegistry registry = new SerdesRegistry();
        StandardSerializers.registerAll(registry);
        return registry;
    }

    /**
     * Registers a serializer for exactly the given class (and its primitive type, for wrappers).
     *
     * @param type the class
     * @param serializer the serializer
     * @param <T> the type
     * @return this registry
     */
    @NotNull
    public <T> SerdesRegistry register(@NotNull Class<T> type, @NotNull TypeSerializer<? super T> serializer) {
        requireNonNull(type, "Type cannot be null");
        Class<?> boxed = Types.box(type);
        return register(candidate -> Types.box(Types.raw(candidate)) == boxed, serializer);
    }

    /**
     * Registers a serializer for the given class and all its subtypes.
     *
     * @param type the class
     * @param serializer the serializer
     * @param <T> the type
     * @return this registry
     */
    @NotNull
    public <T> SerdesRegistry registerHierarchy(@NotNull Class<T> type, @NotNull TypeSerializer<? super T> serializer) {
        requireNonNull(type, "Type cannot be null");
        return register(candidate -> type.isAssignableFrom(Types.box(Types.raw(candidate))), serializer);
    }

    /**
     * Registers a serializer for the types matching a predicate.
     *
     * @param matcher selects the types
     * @param serializer the serializer
     * @return this registry
     */
    @NotNull
    public SerdesRegistry register(@NotNull Predicate<Type> matcher, @NotNull TypeSerializer<?> serializer) {
        requireNonNull(matcher, "Matcher cannot be null");
        requireNonNull(serializer, "Serializer cannot be null");
        entries.add(0, new Entry(matcher, serializer));
        cache.clear();
        return this;
    }

    /**
     * Registers the serializers of a pack.
     *
     * @param pack the pack
     * @return this registry
     */
    @NotNull
    public SerdesRegistry register(@NotNull SerdesPack pack) {
        requireNonNull(pack, "Pack cannot be null").register(this);
        return this;
    }

    /**
     * Finds the serializer for a type: the newest matching registration, else field binding for plain classes
     * and records.
     *
     * @param type the type
     * @return the serializer, or null if the type cannot be converted
     */
    @Nullable
    public TypeSerializer<?> find(@NotNull Type type) {
        requireNonNull(type, "Type cannot be null");
        return cache.computeIfAbsent(type, this::lookup).orElse(null);
    }

    private Optional<TypeSerializer<?>> lookup(Type type) {
        for (Entry entry : entries) {
            if (entry.matcher.test(type)) {
                return Optional.of(entry.serializer);
            }
        }
        if (ObjectSerializer.supports(Types.raw(type))) {
            return Optional.of(ObjectSerializer.INSTANCE);
        }
        return Optional.empty();
    }

    private record Entry(Predicate<Type> matcher, TypeSerializer<?> serializer) {
    }
}
