package org.bxteam.quark.config.validation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Checks the fields of configuration objects after they are loaded. {@code quark-config-validator} provides
 * the annotation-based implementation and registers it with {@link java.util.ServiceLoader}.
 *
 * <p>Every field of the configuration and of its nested objects is checked, with its loaded value or its
 * default. If any check fails the load fails with a {@link ConfigValidationException} listing every
 * violation, the file is left untouched and a reload keeps the previous values.</p>
 */
@FunctionalInterface
public interface ConfigValidator {
    /**
     * @param field the field
     * @param value the value of the field after loading
     * @return the violation messages, empty if the value is valid
     */
    @NotNull
    List<String> validate(@NotNull Field field, @Nullable Object value);
}
