package org.bxteam.quark.config.validator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The number must be at most {@link #value()}. Applies to numbers of any type.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Max {
    /**
     * @return the largest allowed value
     */
    double value();

    /**
     * @return the violation message, {@code {value}} is replaced by the limit
     */
    String message() default "must be at most {value}";
}
