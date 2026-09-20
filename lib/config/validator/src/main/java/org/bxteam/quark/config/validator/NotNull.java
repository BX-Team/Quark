package org.bxteam.quark.config.validator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The value must not be null, for example because the key was set to {@code null} or {@code ~} in the file.
 * The other constraints accept null, so combine them with this one where null is not allowed.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface NotNull {
    /**
     * @return the violation message
     */
    String message() default "must be set";
}
