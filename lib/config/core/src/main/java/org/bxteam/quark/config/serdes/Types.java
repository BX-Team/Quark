package org.bxteam.quark.config.serdes;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

/**
 * Helpers for {@link Type}s.
 */
final class Types {
    private Types() {
    }

    static Class<?> raw(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized) {
            return (Class<?>) parameterized.getRawType();
        }
        if (type instanceof GenericArrayType array) {
            return Array.newInstance(raw(array.getGenericComponentType()), 0).getClass();
        }
        if (type instanceof WildcardType wildcard) {
            return raw(wildcard.getUpperBounds()[0]);
        }
        if (type instanceof TypeVariable<?> variable) {
            return raw(variable.getBounds()[0]);
        }
        return Object.class;
    }

    /**
     * @return the type argument at {@code index}, Object for raw types; wildcards resolve to their upper bound
     */
    static Type argument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length > index) {
            Type argument = parameterized.getActualTypeArguments()[index];
            if (argument instanceof WildcardType wildcard) {
                return wildcard.getUpperBounds()[0];
            }
            return argument;
        }
        return Object.class;
    }

    static Type component(Type type) {
        if (type instanceof GenericArrayType array) {
            return array.getGenericComponentType();
        }
        return raw(type).getComponentType();
    }

    static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == boolean.class) return Boolean.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }

    static String name(Type type) {
        return type instanceof Class<?> clazz ? clazz.getSimpleName() : type.getTypeName();
    }
}
