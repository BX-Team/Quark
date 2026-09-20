package org.bxteam.quark.config.validator;

import org.bxteam.quark.config.validation.ConfigValidator;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Checks {@link NotNull}, {@link Min}, {@link Max}, {@link Pattern} and {@link Check}. Registered with
 * {@link java.util.ServiceLoader}, so every configuration uses it as soon as this module is on the class path.
 */
public final class AnnotationValidator implements ConfigValidator {
    private final Map<String, java.util.regex.Pattern> patterns = new ConcurrentHashMap<>();
    private final Map<Class<?>, Predicate<Object>> predicates = new ConcurrentHashMap<>();

    /**
     * Creates the validator. Used by {@link java.util.ServiceLoader}.
     */
    public AnnotationValidator() {
    }

    @Override
    @org.jetbrains.annotations.NotNull
    public List<String> validate(@org.jetbrains.annotations.NotNull Field field, @Nullable Object value) {
        List<String> violations = new ArrayList<>(0);

        NotNull notNull = field.getAnnotation(NotNull.class);
        if (notNull != null && value == null) {
            violations.add(notNull.message());
        }

        Min min = field.getAnnotation(Min.class);
        if (min != null && value != null) {
            BigDecimal number = number(field, value, "@Min");
            if (number.compareTo(BigDecimal.valueOf(min.value())) < 0) {
                violations.add(format(min.message(), min.value()) + " (was " + value + ")");
            }
        }

        Max max = field.getAnnotation(Max.class);
        if (max != null && value != null) {
            BigDecimal number = number(field, value, "@Max");
            if (number.compareTo(BigDecimal.valueOf(max.value())) > 0) {
                violations.add(format(max.message(), max.value()) + " (was " + value + ")");
            }
        }

        Pattern pattern = field.getAnnotation(Pattern.class);
        if (pattern != null && value != null) {
            java.util.regex.Pattern compiled = patterns.computeIfAbsent(pattern.value(), java.util.regex.Pattern::compile);
            Iterable<?> values = value instanceof Collection<?> collection ? collection : List.of(value);
            for (Object element : values) {
                if (element != null && !compiled.matcher(element.toString()).matches()) {
                    violations.add(pattern.message().replace("{value}", pattern.value()) + " (was '" + element + "')");
                }
            }
        }

        for (Check check : field.getAnnotationsByType(Check.class)) {
            boolean valid;
            try {
                valid = predicate(check.value()).test(value);
            } catch (ClassCastException e) {
                throw new IllegalStateException("@Check(" + check.value().getSimpleName() + ") does not accept the "
                        + field.getType().getSimpleName() + " of " + field, e);
            }
            if (!valid) {
                violations.add(check.message() + " (was " + value + ")");
            }
        }
        return violations;
    }

    private static BigDecimal number(Field field, Object value, String annotation) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof BigInteger integer) return new BigDecimal(integer);
        if (value instanceof Double || value instanceof Float) return BigDecimal.valueOf(((Number) value).doubleValue());
        if (value instanceof Number number) return BigDecimal.valueOf(number.longValue());
        throw new IllegalStateException(annotation + " only applies to numbers, but " + field + " holds a " + value.getClass().getSimpleName());
    }

    private static String format(String message, double limit) {
        String formatted = limit == Math.rint(limit) && !Double.isInfinite(limit) ? String.valueOf((long) limit) : String.valueOf(limit);
        return message.replace("{value}", formatted);
    }

    @SuppressWarnings("unchecked")
    private Predicate<Object> predicate(Class<? extends Predicate<?>> type) {
        return predicates.computeIfAbsent(type, ignored -> {
            try {
                Constructor<?> constructor = type.getDeclaredConstructor();
                constructor.setAccessible(true);
                return (Predicate<Object>) constructor.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot create @Check predicate " + type.getName() + ", it needs a no-argument constructor", e);
            }
        });
    }
}
