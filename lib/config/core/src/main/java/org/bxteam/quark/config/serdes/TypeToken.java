package org.bxteam.quark.config.serdes;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Captures a generic type, for converting values outside of fields.
 *
 * <pre>{@code
 * List<UUID> ids = context.deserialize(node, new TypeToken<List<UUID>>() {});
 * }</pre>
 *
 * @param <T> the captured type
 */
public abstract class TypeToken<T> {
    private final Type type;

    /**
     * Captures the type argument of the anonymous subclass.
     */
    protected TypeToken() {
        Type superclass = getClass().getGenericSuperclass();
        if (!(superclass instanceof ParameterizedType parameterized)) {
            throw new IllegalStateException("TypeToken must be created as an anonymous subclass with a type argument");
        }
        this.type = parameterized.getActualTypeArguments()[0];
    }

    /**
     * @return the captured type
     */
    @NotNull
    public Type type() {
        return type;
    }
}
