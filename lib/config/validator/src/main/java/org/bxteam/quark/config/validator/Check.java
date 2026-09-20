package org.bxteam.quark.config.validator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Predicate;

/**
 * The value must satisfy a custom predicate. The predicate class needs a no-argument constructor and receives
 * the value of the field, which may be null.
 *
 * <pre>{@code
 * public static final class EvenNumber implements Predicate<Integer> {
 *     public boolean test(Integer value) { return value == null || value % 2 == 0; }
 * }
 *
 * @Check(value = EvenNumber.class, message = "must be even")
 * public int slots = 4;
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Repeatable(Check.List.class)
public @interface Check {
    /**
     * @return the predicate class
     */
    Class<? extends Predicate<?>> value();

    /**
     * @return the violation message
     */
    String message() default "is not valid";

    /**
     * Container of repeated {@link Check}s.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface List {
        /**
         * @return the checks
         */
        Check[] value();
    }
}
