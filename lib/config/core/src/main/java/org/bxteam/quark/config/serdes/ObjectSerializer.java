package org.bxteam.quark.config.serdes;

import org.bxteam.quark.config.ConfigNode;
import org.bxteam.quark.config.annotation.Comment;
import org.bxteam.quark.config.annotation.CustomKey;
import org.bxteam.quark.config.annotation.Exclude;
import org.bxteam.quark.config.annotation.NameStrategy;
import org.bxteam.quark.config.annotation.NameStyle;
import org.bxteam.quark.config.validation.ConfigValidator;
import org.bxteam.quark.config.validation.Violation;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Binds plain classes and records field by field: key order is field order (superclass fields first),
 * {@link Comment}s are written above the keys, {@link CustomKey}, {@link Exclude} and {@link NameStrategy}
 * shape the keys. Unknown keys are kept in the context's orphan store when it has one.
 */
final class ObjectSerializer implements TypeSerializer<Object> {
    static final ObjectSerializer INSTANCE = new ObjectSerializer();

    private final Map<Class<?>, Map<NameStyle, List<BoundField>>> fields = new ConcurrentHashMap<>();

    private ObjectSerializer() {
    }

    /**
     * @return true for records and for concrete classes with a no-argument constructor outside of the JDK
     */
    static boolean supports(Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.isEnum() || type.isInterface()
                || Modifier.isAbstract(type.getModifiers()) || type.getName().startsWith("java.")
                || type.getName().startsWith("javax.")) {
            return false;
        }
        if (type.isRecord()) {
            return true;
        }
        try {
            type.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Override
    public Object deserialize(@NotNull Type type, @NotNull ConfigNode node, @NotNull SerdesContext context) {
        return deserialize(type, node, context, null);
    }

    Object deserialize(Type type, ConfigNode node, SerdesContext context, Object existing) {
        if (!node.isNull() && !node.isMap()) {
            throw new SerializationException("expected a section, found " + Scalars.describe(node));
        }
        Class<?> raw = existing != null ? existing.getClass() : Types.raw(type);
        NameStyle previousStyle = context.nameStyle();
        NameStyle style = style(raw, previousStyle);
        context.nameStyle(style);
        try {
            List<BoundField> bound = fields(raw, style);
            Object result = raw.isRecord()
                    ? readRecord(raw, bound, node, context, existing)
                    : readObject(raw, bound, node, context, existing);
            keepOrphans(result, bound, node, context);
            return result;
        } finally {
            context.nameStyle(previousStyle);
        }
    }

    private Object readObject(Class<?> raw, List<BoundField> bound, ConfigNode node, SerdesContext context, Object existing) {
        Object target = existing != null ? existing : instantiate(raw);
        for (BoundField field : bound) {
            ConfigNode child = node.node(field.key);
            Object current = field.get(target);
            Object value = current;
            boolean nested = context.registry().find(field.type) == INSTANCE;
            if (nested && current != null) {
                value = context.deserialize(child, field.type, current);
            } else if (!child.isVirtual()) {
                Object loaded = context.deserialize(child, field.type, null);
                if (loaded != null || !field.field.getType().isPrimitive()) {
                    value = loaded;
                }
            }
            if (value != current) {
                field.set(target, value);
            }
            validate(field, value, child, context);
        }
        return target;
    }

    private Object readRecord(Class<?> raw, List<BoundField> bound, ConfigNode node, SerdesContext context, Object existing) {
        RecordComponent[] components = raw.getRecordComponents();
        Object[] values = new Object[components.length];
        Class<?>[] parameterTypes = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            parameterTypes[i] = components[i].getType();
            String name = components[i].getName();
            BoundField field = bound.stream()
                    .filter(candidate -> candidate.field.getName().equals(name))
                    .findFirst().orElse(null);
            if (field == null) {
                // excluded component: keep the existing value
                values[i] = existing != null ? read(components[i], existing)
                        : components[i].getType().isPrimitive() ? defaultPrimitive(components[i].getType()) : null;
                continue;
            }
            ConfigNode child = node.node(field.key);
            Object current = existing != null ? field.get(existing) : null;
            Object value = current;
            boolean nested = context.registry().find(field.type) == INSTANCE;
            if (nested && current != null) {
                value = context.deserialize(child, field.type, current);
            } else if (!child.isVirtual()) {
                value = context.deserialize(child, field.type, null);
            }
            if (value == null && components[i].getType().isPrimitive()) {
                value = current != null ? current : defaultPrimitive(components[i].getType());
            }
            values[i] = value;
            validate(field, value, child, context);
        }
        try {
            Constructor<?> constructor = raw.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new SerializationException("cannot create " + raw.getSimpleName() + ": "
                    + (cause.getMessage() != null ? cause.getMessage() : cause.toString()), cause);
        } catch (ReflectiveOperationException e) {
            throw new SerializationException("cannot create " + raw.getSimpleName() + ": " + e, e);
        }
    }

    private static Object read(RecordComponent component, Object record) {
        try {
            var accessor = component.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot read " + component, e);
        }
    }

    private static void validate(BoundField field, Object value, ConfigNode node, SerdesContext context) {
        for (ConfigValidator validator : context.validators()) {
            for (String message : validator.validate(field.field, value)) {
                context.addViolation(new Violation(node.pathString(), message));
            }
        }
    }

    private static void keepOrphans(Object target, List<BoundField> bound, ConfigNode node, SerdesContext context) {
        Map<Object, Map<String, ConfigNode>> orphans = context.orphans();
        if (orphans == null || !node.isMap()) {
            return;
        }
        Set<String> keys = new HashSet<>();
        bound.forEach(field -> keys.add(field.key));
        Map<String, ConfigNode> unknown = new LinkedHashMap<>();
        node.children().forEach((key, child) -> {
            if (!keys.contains(key)) {
                unknown.put(key, child.copy());
            }
        });
        if (unknown.isEmpty()) {
            orphans.remove(target);
        } else {
            orphans.put(target, unknown);
        }
    }

    @Override
    public void serialize(@NotNull Type type, @NotNull Object value, @NotNull ConfigNode node, @NotNull SerdesContext context) {
        NameStyle previousStyle = context.nameStyle();
        NameStyle style = style(value.getClass(), previousStyle);
        context.nameStyle(style);
        try {
            node.setMap();
            List<BoundField> bound = fields(value.getClass(), style);
            for (BoundField field : bound) {
                ConfigNode child = node.node(field.key);
                context.serialize(field.get(value), field.type, child);
                if (!field.comment.isEmpty()) {
                    child.comment(field.comment);
                }
            }
            Map<Object, Map<String, ConfigNode>> orphans = context.orphans();
            Map<String, ConfigNode> unknown = orphans != null ? orphans.get(value) : null;
            if (unknown != null) {
                unknown.forEach((key, orphan) -> {
                    ConfigNode child = node.node(key);
                    if (child.isVirtual()) {
                        child.set(orphan);
                    }
                });
            }
        } finally {
            context.nameStyle(previousStyle);
        }
    }

    /**
     * @return the bound fields of a class, superclass fields first
     */
    List<BoundField> fields(Class<?> type, NameStyle style) {
        return fields.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(style, ignored -> scan(type, style));
    }

    private static List<BoundField> scan(Class<?> type, NameStyle style) {
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class && current != Record.class; current = current.getSuperclass()) {
            hierarchy.add(0, current);
        }

        List<BoundField> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Class<?> declaring : hierarchy) {
            for (Field field : declaring.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers) || field.isSynthetic()
                        || field.isAnnotationPresent(Exclude.class)) {
                    continue;
                }
                CustomKey customKey = field.getAnnotation(CustomKey.class);
                String key = customKey != null ? customKey.value() : style.apply(field.getName());
                if (!keys.add(key)) {
                    throw new IllegalStateException("Duplicate configuration key '" + key + "' in " + type.getName()
                            + " (field " + declaring.getSimpleName() + "#" + field.getName() + ")");
                }
                Comment comment = field.getAnnotation(Comment.class);
                try {
                    field.setAccessible(true);
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Cannot access configuration field " + declaring.getName() + "#"
                            + field.getName() + ", open its package to Quark", e);
                }
                result.add(new BoundField(field, field.getGenericType(), key, comment != null ? List.of(comment.value()) : List.of()));
            }
        }
        return List.copyOf(result);
    }

    private static NameStyle style(Class<?> type, NameStyle inherited) {
        NameStrategy strategy = type.getAnnotation(NameStrategy.class);
        return strategy != null ? strategy.value() : inherited;
    }

    private static Object instantiate(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (InvocationTargetException e) {
            throw new SerializationException("cannot create " + type.getSimpleName() + ": " + e.getCause(), e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new SerializationException("cannot create " + type.getSimpleName()
                    + ", it needs a no-argument constructor: " + e, e);
        }
    }

    private static Object defaultPrimitive(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        return 0;
    }

    /**
     * A field bound to a key.
     */
    record BoundField(Field field, Type type, String key, List<String> comment) {
        Object get(Object target) {
            try {
                return field.get(target);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read " + field, e);
            }
        }

        void set(Object target, Object value) {
            try {
                field.set(target, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot write " + field, e);
            }
        }
    }
}
