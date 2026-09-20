package org.bxteam.quark.config.validator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The string must match a regular expression as a whole. On a collection, every element must match.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Pattern {
    /**
     * @return the regular expression
     */
    String value();

    /**
     * @return the violation message, {@code {value}} is replaced by the expression
     */
    String message() default "must match {value}";
}
