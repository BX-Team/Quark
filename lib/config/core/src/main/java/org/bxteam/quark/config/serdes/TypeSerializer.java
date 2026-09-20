package org.bxteam.quark.config.serdes;

import org.bxteam.quark.config.ConfigNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Converts values of a type to and from {@link ConfigNode}s. Registered in a {@link SerdesRegistry},
 * usually as part of a {@link SerdesPack}.
 *
 * <p>Serializers never see {@code null}: the {@link SerdesContext} maps null and missing nodes to
 * {@link #emptyValue(Type)} and null values to null nodes. Nested values are converted through the context,
 * so they use whatever serializers are registered.</p>
 *
 * @param <T> the type
 */
public interface TypeSerializer<T> {
    /**
     * Reads a value from a node that is neither missing nor null.
     *
     * @param type the requested type, with generic arguments if the field declares them
     * @param node the node
     * @param context the context, for nested values
     * @return the value
     * @throws SerializationException if the node cannot be converted
     */
    @Nullable
    T deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context);

    /**
     * Writes a non-null value into an empty node.
     *
     * @param type the declared type, with generic arguments if the field declares them
     * @param value the value
     * @param node the node to write into
     * @param context the context, for nested values
     * @throws SerializationException if the value cannot be converted
     */
    void serialize(@NotNull Type type, @NotNull T value, @NotNull ConfigNode node, @NotNull SerdesContext context);

    /**
     * The value of a null or missing node, {@code null} by default ({@code Optional.empty()} for {@code Optional}).
     *
     * @param type the requested type
     * @return the value
     */
    @Nullable
    default T emptyValue(@NotNull Type type) {
        return null;
    }

    /**
     * Creates a serializer for a type stored as a single string.
     *
     * <pre>{@code
     * registry.register(NamespacedKey.class, TypeSerializer.ofString(NamespacedKey::fromString, NamespacedKey::toString));
     * }</pre>
     *
     * @param parse converts the string to a value, may throw {@link IllegalArgumentException}
     * @param format converts a value to the string
     * @param <T> the type
     * @return the serializer
     */
    @NotNull
    static <T> TypeSerializer<T> ofString(@NotNull Function<String, T> parse, @NotNull Function<T, String> format) {
        requireNonNull(parse, "Parse function cannot be null");
        requireNonNull(format, "Format function cannot be null");
        return new TypeSerializer<>() {
            @Override
            public T deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
                String string = Scalars.string(node);
                try {
                    T value = parse.apply(string);
                    if (value == null) {
                        throw new SerializationException("'" + string + "' is not a valid " + Types.name(type));
                    }
                    return value;
                } catch (IllegalArgumentException e) {
                    throw new SerializationException("'" + string + "' is not a valid " + Types.name(type)
                            + (e.getMessage() != null ? ": " + e.getMessage() : ""), e);
                }
            }

            @Override
            public void serialize(@NotNull Type type, @NotNull T value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
                node.set(format.apply(value));
            }
        };
    }
}
