package org.bxteam.quark.config.serdes;

import org.bxteam.quark.config.ConfigNode;
import org.bxteam.quark.config.annotation.NameStyle;
import org.bxteam.quark.config.validation.ConfigValidator;
import org.bxteam.quark.config.validation.Violation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * Converts values with the serializers of a {@link SerdesRegistry}. Passed to every {@link TypeSerializer}
 * for nested values, and usable on its own to bind objects outside of a {@link org.bxteam.quark.config.QuarkConfig}:
 *
 * <pre>{@code
 * SerdesContext context = new SerdesContext(SerdesRegistry.create());
 * Settings settings = context.deserialize(node, Settings.class);
 * }</pre>
 *
 * <p>A context collects the validation {@link #violations()} and the unknown keys of one load, so it is not
 * meant to be shared between threads.</p>
 */
public final class SerdesContext {
    private final SerdesRegistry registry;
    private final List<ConfigValidator> validators;
    private final Map<Object, Map<String, ConfigNode>> orphans;
    private final List<Violation> violations = new ArrayList<>();
    private NameStyle nameStyle = NameStyle.IDENTITY;

    /**
     * Creates a context without validation that drops unknown keys.
     *
     * @param registry the serializers
     */
    public SerdesContext(@NotNull SerdesRegistry registry) {
        this(registry, List.of(), null);
    }

    /**
     * @param registry the serializers
     * @param validators the validators run on every bound field
     * @param orphans where unknown keys of bound objects are kept, by object identity, or null to drop them
     */
    @ApiStatus.Internal
    public SerdesContext(@NotNull SerdesRegistry registry, @NotNull List<ConfigValidator> validators,
                         @Nullable IdentityHashMap<Object, Map<String, ConfigNode>> orphans) {
        this.registry = requireNonNull(registry, "Registry cannot be null");
        this.validators = List.copyOf(requireNonNull(validators, "Validators cannot be null"));
        this.orphans = orphans;
    }

    /**
     * @return the serializers
     */
    @NotNull
    public SerdesRegistry registry() {
        return registry;
    }

    /**
     * @param node the node
     * @param type the type
     * @param <T> the type
     * @return the value, or the empty value of the type for a null or missing node
     * @throws SerializationException if the node cannot be converted
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T deserialize(@NotNull ConfigNode node, @NotNull Class<T> type) {
        return (T) deserialize(node, (Type) type);
    }

    /**
     * @param node the node
     * @param type the type
     * @param <T> the type
     * @return the value, or the empty value of the type for a null or missing node
     * @throws SerializationException if the node cannot be converted
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T deserialize(@NotNull ConfigNode node, @NotNull TypeToken<T> type) {
        return (T) deserialize(node, type.type());
    }

    /**
     * @param node the node
     * @param type the type
     * @return the value, or the empty value of the type for a null or missing node
     * @throws SerializationException if the node cannot be converted
     */
    @Nullable
    public Object deserialize(@NotNull ConfigNode node, @NotNull Type type) {
        return deserialize(node, type, null);
    }

    /**
     * Reads a value, filling {@code existing} instead of creating a new object when the type is bound field by
     * field; missing keys then keep the values of {@code existing}.
     */
    Object deserialize(ConfigNode node, Type type, Object existing) {
        requireNonNull(node, "Node cannot be null");
        requireNonNull(type, "Type cannot be null");
        TypeSerializer<?> serializer = serializer(type);
        try {
            if (serializer == ObjectSerializer.INSTANCE && (existing != null || !node.isNull())) {
                return ObjectSerializer.INSTANCE.deserialize(type, node, this, existing);
            }
            if (node.isNull()) {
                return serializer.emptyValue(type);
            }
            return serializer.deserialize(type, node, this);
        } catch (SerializationException e) {
            e.initPath(node.pathString());
            throw e;
        } catch (RuntimeException e) {
            SerializationException wrapped = new SerializationException(e.getMessage() != null ? e.getMessage() : e.toString(), e);
            wrapped.initPath(node.pathString());
            throw wrapped;
        }
    }

    /**
     * Fills an existing object, such as a configuration instance, from a node. Missing keys keep the
     * values of the object.
     *
     * @param node the node
     * @param target the object
     * @throws SerializationException if a value cannot be converted
     */
    public void deserializeInto(@NotNull ConfigNode node, @NotNull Object target) {
        requireNonNull(target, "Target cannot be null");
        try {
            ObjectSerializer.INSTANCE.deserialize(target.getClass(), node, this, target);
        } catch (SerializationException e) {
            e.initPath(node.pathString());
            throw e;
        }
    }

    /**
     * Writes a value into a node, replacing its content.
     *
     * @param value the value
     * @param type the declared type; the class of the value is used when the declared type has no serializer
     * @param node the node
     * @throws SerializationException if the value cannot be converted
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void serialize(@Nullable Object value, @NotNull Type type, @NotNull ConfigNode node) {
        requireNonNull(type, "Type cannot be null");
        requireNonNull(node, "Node cannot be null");
        if (value == null) {
            node.set(null);
            return;
        }
        Type effective = type;
        TypeSerializer serializer = registry.find(type);
        if (serializer == null || type == Object.class) {
            effective = value.getClass();
            serializer = serializer(effective);
        }
        try {
            node.set(null);
            serializer.serialize(effective, value, node, this);
        } catch (SerializationException e) {
            e.initPath(node.pathString());
            throw e;
        } catch (RuntimeException e) {
            SerializationException wrapped = new SerializationException(e.getMessage() != null ? e.getMessage() : e.toString(), e);
            wrapped.initPath(node.pathString());
            throw wrapped;
        }
    }

    /**
     * Writes the fields of an object, such as a configuration instance, into a node, keeping the keys the node
     * already has in front.
     *
     * @param source the object
     * @param node the node
     * @throws SerializationException if a value cannot be converted
     */
    public void serializeInto(@NotNull Object source, @NotNull ConfigNode node) {
        requireNonNull(source, "Source cannot be null");
        requireNonNull(node, "Node cannot be null");
        try {
            ObjectSerializer.INSTANCE.serialize(source.getClass(), source, node, this);
        } catch (SerializationException e) {
            e.initPath(node.pathString());
            throw e;
        }
    }

    /**
     * @return the validation violations collected so far
     */
    @NotNull
    @Unmodifiable
    public List<Violation> violations() {
        return List.copyOf(violations);
    }

    private TypeSerializer<?> serializer(Type type) {
        TypeSerializer<?> serializer = registry.find(type);
        if (serializer == null) {
            throw new SerializationException("No serializer for " + type.getTypeName()
                    + ". Register a TypeSerializer for it, or give the class a no-argument constructor");
        }
        return serializer;
    }

    List<ConfigValidator> validators() {
        return validators;
    }

    void addViolation(Violation violation) {
        violations.add(violation);
    }

    Map<Object, Map<String, ConfigNode>> orphans() {
        return orphans;
    }

    NameStyle nameStyle() {
        return nameStyle;
    }

    void nameStyle(NameStyle style) {
        this.nameStyle = style;
    }
}
